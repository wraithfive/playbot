package com.discordbot;

import com.discordbot.entity.QotdStream;
import com.discordbot.entity.UserCooldown;
import com.discordbot.repository.QotdStreamRepository;
import com.discordbot.repository.UserCooldownRepository;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.QotdSubmissionService;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.awt.Color;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class SlashCommandHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SlashCommandHandler.class);

    private static final String GACHA_PREFIX = "gacha:";

    // D20 animation constants
    private static final int D20_ANIMATION_FRAMES = 6;
    private static final long D20_FRAME_DELAY_MS = 500;
    private static final long D20_WINDOW_MINUTES = 60;

    private final UserCooldownRepository cooldownRepository;
    private final QotdStreamRepository streamRepository;
    private final GuildsCache guildsCache;
    private final WebSocketNotificationService webSocketNotificationService;
    private final QotdSubmissionService qotdSubmissionService;
    private final Random random = new Random();

    @Autowired
    public SlashCommandHandler(
            UserCooldownRepository cooldownRepository,
            QotdStreamRepository streamRepository,
            GuildsCache guildsCache,
            WebSocketNotificationService webSocketNotificationService,
            QotdSubmissionService qotdSubmissionService) {
        this.cooldownRepository = cooldownRepository;
        this.streamRepository = streamRepository;
        this.guildsCache = guildsCache;
        this.webSocketNotificationService = webSocketNotificationService;
        this.qotdSubmissionService = qotdSubmissionService;
        logger.info("SlashCommandHandler initialized with database persistence, QOTD submissions, stream autocomplete, and WebSocket notifications");
    }


    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("Playbot Slash Command Handler initialized and ready");
        logger.info("Logged in as: {}", event.getJDA().getSelfUser().getName());
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        // Register slash commands for this guild
        event.getGuild().updateCommands().addCommands(
            Commands.slash("roll", "Roll for a random gacha role (once per day)"),
            Commands.slash("d20", "Roll a d20 for bonus/penalty (60 min after /roll)"),
            Commands.slash("testroll", "Test roll without cooldown (admin only)"),
            Commands.slash("mycolor", "Check your current gacha role"),
            Commands.slash("colors", "View all available gacha roles"),
            Commands.slash("help", "Show help information"),
            Commands.slash("qotd-submit", "Suggest a Question of the Day for admins to review")
                .addOption(OptionType.STRING, "question", "Your question (max 300 chars)", true)
                .addOption(OptionType.STRING, "stream", "Target stream (optional)", false, true)
        ).queue(
            success -> logger.info("Registered slash commands for guild: {}", event.getGuild().getName()),
            error -> logger.error("Failed to register slash commands for guild {}: {}",
                event.getGuild().getName(), error.getMessage())
        );
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        String guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();
        
        // Evict cache so backend reflects the change immediately
        if (guildsCache != null) guildsCache.evictAll();
        
        // Notify all connected WebSocket clients
        webSocketNotificationService.notifyGuildJoined(guildId, guildName);
        
        event.getGuild().updateCommands().addCommands(
            Commands.slash("roll", "Roll for a random gacha role (once per day)"),
            Commands.slash("d20", "Roll a d20 for bonus/penalty (60 min after /roll)"),
            Commands.slash("testroll", "Test roll without cooldown (admin only)"),
            Commands.slash("mycolor", "Check your current gacha role"),
            Commands.slash("colors", "View all available gacha roles"),
            Commands.slash("help", "Show help information"),
            Commands.slash("qotd-submit", "Suggest a Question of the Day for admins to review")
                .addOption(OptionType.STRING, "question", "Your question (max 300 chars)", true)
                .addOption(OptionType.STRING, "stream", "Target stream (optional)", false, true)
        ).queue(
            success -> logger.info("Registered slash commands for newly joined guild: {}", guildName),
            error -> logger.error("Failed to register slash commands on guild join for {}: {}", guildName, error.getMessage())
        );
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        String guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();
        
        // Evict cache so backend reflects the change immediately
        if (guildsCache != null) guildsCache.evictAll();
        
        // Notify all connected WebSocket clients
        webSocketNotificationService.notifyGuildLeft(guildId, guildName);
        
        try {
            cooldownRepository.deleteByGuildId(guildId);
            logger.info("Cleaned up guild-specific data for guild {} on bot leave", guildName);
        } catch (Exception e) {
            logger.error("Failed to clean up data for guild {} ({}): {}", guildName, guildId, e.getMessage());
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        switch (commandName) {
            case "roll" -> handleRoll(event, false);
            case "d20" -> handleD20(event);
            case "testroll" -> handleTestRoll(event);
            case "mycolor" -> handleMyColor(event);
            case "colors" -> handleColors(event);
            case "help" -> handleHelp(event);
            case "qotd-submit" -> handleQotdSubmit(event);
            default -> event.reply("Unknown command!").setEphemeral(true).queue();
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals("qotd-submit") && event.getFocusedOption().getName().equals("stream")) {
            String guildId = event.getGuild().getId();
            List<QotdStream> streams = streamRepository.findByGuildIdOrderByChannelIdAscIdAsc(guildId);

            List<Command.Choice> choices = streams.stream()
                .map(s -> new Command.Choice(s.getBannerText(), String.valueOf(s.getId())))
                .limit(25)  // Discord max
                .toList();

            event.replyChoices(choices).queue();
        }
    }

    private void handleQotdSubmit(SlashCommandInteractionEvent event) {
        if (event.getMember() == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        String username = event.getUser().getName();
        String questionText = event.getOption("question").getAsString();

        // Get optional stream parameter
        Long targetStreamId = null;
        if (event.getOption("stream") != null) {
            try {
                targetStreamId = Long.parseLong(event.getOption("stream").getAsString());
            } catch (NumberFormatException e) {
                event.reply("❌ Invalid stream selection. Please try again.").setEphemeral(true).queue();
                return;
            }
        }

        try {
            qotdSubmissionService.submit(guildId, userId, username, questionText, targetStreamId);

            String responseMessage = targetStreamId != null
                ? "✓ Your question has been submitted to the selected stream for admin review. Thanks for contributing!"
                : "✓ Your question has been submitted for admin review. Thanks for contributing!";

            event.reply(responseMessage).setEphemeral(true).queue();
            logger.info("QOTD submission from {} in guild {} (stream: {}): {}", username, guildId, targetStreamId, questionText);
        } catch (IllegalArgumentException | IllegalStateException e) {
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Failed to submit QOTD for user {} in guild {}: {}", userId, guildId, e.getMessage());
            event.reply("❌ Failed to submit question. Please try again later.").setEphemeral(true).queue();
        }
    }

    private void handleRoll(SlashCommandInteractionEvent event, boolean isTest) {
        Member member = event.getMember();
        if (member == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        String userId = event.getUser().getId();
        String guildId = event.getGuild().getId();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        // Check cooldown (skip for test rolls)
        if (!isTest) {
            Optional<UserCooldown> cooldownOpt = cooldownRepository.findByUserIdAndGuildId(userId, guildId);
            if (cooldownOpt.isPresent()) {
                UserCooldown cooldown = cooldownOpt.get();
                LocalDate lastRoll = cooldown.getLastRollTime().toLocalDate();
                if (lastRoll.equals(today)) {
                    event.reply("⏰ You've already rolled today! Come back tomorrow for another chance!")
                        .setEphemeral(true).queue();
                    logger.info("User {} attempted duplicate roll in guild {}",
                        event.getUser().getName(), event.getGuild().getName());
                    return;
                }
            }
        }

        // Get all gacha roles
        List<RoleInfo> gachaRoles = event.getGuild().getRoles().stream()
            .filter(role -> role.getName().toLowerCase().startsWith(GACHA_PREFIX))
            .map(this::parseRoleInfo)
            .filter(Objects::nonNull)
            .toList();

        if (gachaRoles.isEmpty()) {
            event.reply("❌ No gacha roles configured! Ask a server admin to set them up.")
                .setEphemeral(true).queue();
            return;
        }

        // Check if user has Epic+ buff from nat 20 (works for both regular and test rolls)
        boolean hasEpicPlusBuff = false;
        Optional<UserCooldown> cooldownOpt = cooldownRepository.findByUserIdAndGuildId(userId, guildId);
        if (cooldownOpt.isPresent() && cooldownOpt.get().isGuaranteedEpicPlus()) {
            hasEpicPlusBuff = true;
        }

        // Roll a random role based on rarity weights (filter to Epic+ if buff active)
        RoleInfo rolledRole;
        if (hasEpicPlusBuff) {
            List<RoleInfo> epicPlusRoles = gachaRoles.stream()
                .filter(r -> r.rarity == Rarity.EPIC || r.rarity == Rarity.LEGENDARY)
                .toList();

            if (epicPlusRoles.isEmpty()) {
                // Fallback if no Epic+ roles (shouldn't happen if d20 validation works)
                rolledRole = rollRandomRole(gachaRoles);
            } else {
                rolledRole = rollRandomRole(epicPlusRoles);
            }
        } else {
            rolledRole = rollRandomRole(gachaRoles);
        }
        Role discordRole = event.getGuild().getRoleById(rolledRole.roleId);

        if (discordRole == null) {
            event.reply("❌ Error: Selected role no longer exists!").setEphemeral(true).queue();
            return;
        }

        // Remove old gacha roles
        List<Role> currentGachaRoles = member.getRoles().stream()
            .filter(role -> role.getName().toLowerCase().startsWith(GACHA_PREFIX))
            .toList();

        try {
            // Remove old roles
            for (Role oldRole : currentGachaRoles) {
                event.getGuild().removeRoleFromMember(member, oldRole).complete();
            }

            // Add new role
            event.getGuild().addRoleToMember(member, discordRole).complete();

            // Update cooldown in database (always save, even for test rolls, so d20 can be tested)
            Optional<UserCooldown> existingCooldown = cooldownRepository.findByUserIdAndGuildId(userId, guildId);
            if (existingCooldown.isPresent()) {
                // Update existing record
                UserCooldown cooldown = existingCooldown.get();
                cooldown.setLastRollTime(now);
                cooldown.setUsername(event.getUser().getName());
                cooldown.setD20Used(false); // Reset d20 usage for new roll cycle
                cooldown.setGuaranteedEpicPlus(false); // Clear buff after use
                cooldownRepository.save(cooldown);
            } else {
                // Create new record
                UserCooldown newCooldown = new UserCooldown(userId, guildId, now, event.getUser().getName());
                cooldownRepository.save(newCooldown);
            }
            logger.debug("Updated cooldown for user {} in guild {}{}", userId, guildId, isTest ? " [TEST]" : "");

            // Create response embed
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("🎲 Gacha Roll Result");
            embed.setColor(discordRole.getColor() != null ? discordRole.getColor() : Color.MAGENTA);

            String rarityEmoji = getRarityEmoji(rolledRole.rarity);
            String description = String.format("You rolled: **%s** %s\n\nRarity: **%s**",
                rolledRole.displayName,
                rarityEmoji,
                rolledRole.rarity != null ? rolledRole.rarity.name() : "Unknown");

            if (hasEpicPlusBuff) {
                description += "\n\n✨ **Lucky Streak used!** You were guaranteed Epic or higher!";
            }
            embed.setDescription(description);

            if (rolledRole.rarity != null) {
                embed.addField("Drop Rate", String.format("%.1f%%", getRarityWeight(rolledRole.rarity) * 100), true);
            }

            // Add d20 hint if conditions are met (3+ Epic roles and not test)
            if (!isTest && countEpicPlusRoles(gachaRoles) >= 3) {
                embed.addField("🎲 Feeling Lucky?",
                    "Use `/d20` within 60 minutes to roll for a bonus or penalty!",
                    false);
            }

            if (!isTest) {
                embed.setFooter("Come back tomorrow for another roll!");
            } else {
                embed.setFooter("Test roll - no cooldown applied");
            }
            embed.setTimestamp(Instant.now());

            // Regular rolls are public (for fun/hype), test rolls are private
            event.replyEmbeds(embed.build()).setEphemeral(isTest).queue();

            logger.info("User {} rolled {} (rarity: {}) in guild {}{}",
                event.getUser().getName(),
                rolledRole.displayName,
                rolledRole.rarity != null ? rolledRole.rarity : "Unknown",
                event.getGuild().getName(),
                isTest ? " [TEST]" : "");

        } catch (Exception e) {
            logger.error("Failed to assign role in guild {}: {}", event.getGuild().getName(), e.getMessage(), e);

            // Provide helpful error message based on the issue
            String errorMessage = "❌ Failed to assign role!\n\n";

            if (e.getMessage() != null && e.getMessage().contains("Missing Permissions")) {
                errorMessage += "**Issue:** Bot doesn't have permission to manage roles.\n\n";
                errorMessage += "**Fix:** Server admin needs to:\n";
                errorMessage += "1. Go to Server Settings → Roles\n";
                errorMessage += "2. Move Playbot's role ABOVE the gacha roles\n";
                errorMessage += "3. Ensure Playbot has 'Manage Roles' permission";
            } else if (e.getMessage() != null && e.getMessage().contains("hierarchy")) {
                errorMessage += "**Issue:** Playbot's role is too low in the role hierarchy.\n\n";
                errorMessage += "**Fix:** Server admin needs to:\n";
                errorMessage += "1. Go to Server Settings → Roles\n";
                errorMessage += "2. Drag Playbot's role ABOVE all gacha roles\n";
                errorMessage += "3. Try rolling again";
            } else {
                errorMessage += "**Issue:** " + (e.getMessage() != null ? e.getMessage() : "Unknown error") + "\n\n";
                errorMessage += "Contact your server admin for help.";
            }

            event.reply(errorMessage).setEphemeral(true).queue();
        }
    }

    private void handleTestRoll(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        // Check if user has admin or moderator permissions
        boolean isAdmin = member.hasPermission(Permission.ADMINISTRATOR) ||
                         member.hasPermission(Permission.MANAGE_SERVER) ||
                         member.hasPermission(Permission.MANAGE_ROLES);

        if (!isAdmin) {
            event.reply("❌ This command is only available to server administrators and moderators!")
                .setEphemeral(true).queue();
            return;
        }

        handleRoll(event, true);
    }

    private void handleMyColor(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        String userId = event.getUser().getId();
        String guildId = event.getGuild().getId();

        // Find user's current gacha role
        Optional<Role> currentRole = member.getRoles().stream()
            .filter(role -> role.getName().toLowerCase().startsWith(GACHA_PREFIX))
            .findFirst();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎨 Your Current Color");
        embed.setTimestamp(Instant.now());

        if (currentRole.isPresent()) {
            Role role = currentRole.get();
            RoleInfo roleInfo = parseRoleInfo(role);

            embed.setColor(role.getColor() != null ? role.getColor() : Color.MAGENTA);
            embed.setDescription(String.format("**%s** %s",
                roleInfo.displayName,
                getRarityEmoji(roleInfo.rarity)));

            if (roleInfo.rarity != null) {
                embed.addField("Rarity", roleInfo.rarity.name(), true);
                embed.addField("Drop Rate", String.format("%.1f%%", getRarityWeight(roleInfo.rarity) * 100), true);
            }

            embed.setFooter("Use /roll to get a new color tomorrow!");
        } else {
            embed.setColor(Color.GRAY);
            embed.setDescription("You don't have any gacha role yet!\n\nUse `/roll` to get your first color!");
        }

        // Check for cooldown status and d20 buffs
        Optional<UserCooldown> cooldownOpt = cooldownRepository.findByUserIdAndGuildId(userId, guildId);
        if (cooldownOpt.isPresent()) {
            UserCooldown cooldown = cooldownOpt.get();
            LocalDateTime now = LocalDateTime.now();

            // Show Epic+ buff if active
            if (cooldown.isGuaranteedEpicPlus()) {
                embed.addField("🎲 Lucky Streak Active!",
                    "Your next roll is guaranteed to be Epic or Legendary!",
                    false);
            }

            // Always show roll cooldown status
            long hoursUntilRoll = java.time.Duration.between(now, cooldown.getLastRollTime().plusHours(24)).toHours();
            long minutesUntilRoll = java.time.Duration.between(now, cooldown.getLastRollTime().plusHours(24)).toMinutes();

            if (hoursUntilRoll > 24) {
                // Extended cooldown from nat 1
                embed.addField("⏳ Next Roll Available",
                    String.format("**%d hours** remaining (Critical Failure penalty)", hoursUntilRoll),
                    false);
            } else if (minutesUntilRoll > 0) {
                // Normal cooldown
                if (hoursUntilRoll > 0) {
                    long remainingMinutes = minutesUntilRoll % 60;
                    embed.addField("⏳ Next Roll Available",
                        String.format("In **%d hours %d minutes**", hoursUntilRoll, remainingMinutes),
                        false);
                } else {
                    embed.addField("⏳ Next Roll Available",
                        String.format("In **%d minutes**", minutesUntilRoll),
                        false);
                }
            } else {
                // Ready to roll
                embed.addField("✅ Ready to Roll!",
                    "Use `/roll` to get a new color now!",
                    false);
            }

            // Always show d20 status (check if feature is available first)
            List<Role> gachaRoles = event.getGuild().getRoles().stream()
                .filter(r -> r.getName().toLowerCase().startsWith(GACHA_PREFIX))
                .toList();
            long epicLegendaryCount = gachaRoles.stream()
                .map(this::parseRoleInfo)
                .filter(ri -> ri.rarity == Rarity.EPIC || ri.rarity == Rarity.LEGENDARY)
                .count();

            if (epicLegendaryCount >= 3) {
                if (!cooldown.isD20Used() && isWithinD20Window(cooldown)) {
                    long minutesLeft = D20_WINDOW_MINUTES - java.time.Duration.between(cooldown.getLastRollTime(), now).toMinutes();
                    embed.addField("🎲 /d20 Available",
                        String.format("Use `/d20` within **%d minutes** for a bonus or penalty!", minutesLeft),
                        false);
                } else if (cooldown.isD20Used()) {
                    embed.addField("🎲 /d20 Status",
                        "Already used this cycle. Use `/roll` to reset.",
                        false);
                } else {
                    embed.addField("🎲 /d20 Status",
                        "Use `/roll` to enable `/d20` for 60 minutes.",
                        false);
                }
            }
        } else {
            // No cooldown record yet
            embed.addField("✅ Ready to Roll!",
                "Use `/roll` to get your first color!",
                false);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleColors(SlashCommandInteractionEvent event) {
        List<RoleInfo> gachaRoles = event.getGuild().getRoles().stream()
            .filter(role -> role.getName().toLowerCase().startsWith(GACHA_PREFIX))
            .map(this::parseRoleInfo)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(r -> r.rarity != null ? r.rarity.ordinal() : 999))
            .toList();

        if (gachaRoles.isEmpty()) {
            event.reply("❌ No gacha roles configured! Ask a server admin to set them up.")
                .setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎨 Available Colors");
        embed.setColor(Color.MAGENTA);
        embed.setDescription("Here are all the gacha roles you can roll for:");

        // Group by rarity
        Map<Rarity, List<RoleInfo>> byRarity = gachaRoles.stream()
            .collect(Collectors.groupingBy(
                r -> r.rarity != null ? r.rarity : Rarity.COMMON,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        // Add fields for each rarity (in reverse order - legendary first)
        List<Rarity> sortedRarities = Arrays.asList(Rarity.LEGENDARY, Rarity.EPIC, Rarity.RARE, Rarity.UNCOMMON, Rarity.COMMON);
        for (Rarity rarity : sortedRarities) {
            if (byRarity.containsKey(rarity)) {
                List<String> roleNames = byRarity.get(rarity).stream()
                    .map(r -> r.displayName)
                    .toList();

                String rarityEmoji = getRarityEmoji(rarity);
                embed.addField(
                    String.format("%s %s (%.1f%%)", rarityEmoji, rarity.name(), getRarityWeight(rarity) * 100),
                    String.join(", ", roleNames),
                    false
                );
            }
        }

        embed.setFooter("Use /roll to get a random color!");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleHelp(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        boolean isAdmin = member != null && (
            member.hasPermission(Permission.ADMINISTRATOR) ||
            member.hasPermission(Permission.MANAGE_SERVER)
        );

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎨 Playbot - Help");
        embed.setColor(Color.MAGENTA);
        embed.setDescription("Roll for a random color every day to change your name color!\n\nServer admins can create gacha roles with format:\n`gacha:rarity:ColorName` or `gacha:ColorName`");

        embed.addField("/roll", "Roll for a random color (once per day)", false);
        embed.addField("/testroll", "Test roll without cooldown (admins only)", false);
        embed.addField("/mycolor", "Check your current color", false);
        embed.addField("/colors", "View all available colors and their rarities", false);
        embed.addField("/help", "Show this help message", false);
        embed.addField("", "**Valid Rarities:** common, uncommon, rare, epic, legendary", false);

        // Add admin panel and legal links
        String adminPanelUrl = System.getProperty("ADMIN_PANEL_URL", "http://localhost:3000");

        // Build links section - only include admin panel for admins
        StringBuilder linksText = new StringBuilder();
        if (isAdmin) {
            linksText.append(String.format("**Admin Panel:** %s\n", adminPanelUrl));
        }
        linksText.append(String.format("**Privacy Policy:** %s/privacy\n", adminPanelUrl));
        linksText.append(String.format("**Terms of Service:** %s/terms", adminPanelUrl));

        embed.addField("📋 Links", linksText.toString(), false);

        embed.setFooter("Good luck with your rolls! By using this bot, you agree to our Terms of Service.");
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    // Helper methods

    private RoleInfo parseRoleInfo(Role role) {
        String fullName = role.getName().toLowerCase();
        if (!fullName.startsWith(GACHA_PREFIX)) {
            return null;
        }

        String afterPrefix = fullName.substring(GACHA_PREFIX.length());
        String[] parts = afterPrefix.split(":");

        Rarity rarity = null;
        String displayName = afterPrefix;

        if (parts.length >= 2) {
            // Format: gacha:rarity:ColorName
            try {
                rarity = Rarity.valueOf(parts[0].toUpperCase());
                displayName = afterPrefix.substring(parts[0].length() + 1);
            } catch (IllegalArgumentException e) {
                // Invalid rarity, treat whole thing as name
            }
        }

        return new RoleInfo(role.getId(), displayName, rarity);
    }

    private RoleInfo rollRandomRole(List<RoleInfo> roles) {
        // Calculate total weight
        double totalWeight = roles.stream()
            .mapToDouble(r -> getRarityWeight(r.rarity))
            .sum();

        // Random roll
        double roll = random.nextDouble() * totalWeight;

        // Find the role
        double currentWeight = 0;
        for (RoleInfo role : roles) {
            currentWeight += getRarityWeight(role.rarity);
            if (roll < currentWeight) {
                return role;
            }
        }

        // Fallback (shouldn't happen)
        return roles.get(random.nextInt(roles.size()));
    }

    private double getRarityWeight(Rarity rarity) {
        if (rarity == null) return 0.40; // Common default
        return switch (rarity) {
            case LEGENDARY -> 0.01;  // 1%
            case EPIC -> 0.05;       // 5%
            case RARE -> 0.10;       // 10%
            case UNCOMMON -> 0.24;   // 24%
            case COMMON -> 0.40;     // 40%
        };
    }

    private String getRarityEmoji(Rarity rarity) {
        if (rarity == null) return "⚪";
        return switch (rarity) {
            case LEGENDARY -> "🟡";
            case EPIC -> "🟣";
            case RARE -> "🔵";
            case UNCOMMON -> "🟢";
            case COMMON -> "⚪";
        };
    }

    /**
     * Count the number of Epic and Legendary roles
     */
    private long countEpicPlusRoles(List<RoleInfo> roles) {
        return roles.stream()
            .filter(r -> r.rarity == Rarity.EPIC || r.rarity == Rarity.LEGENDARY)
            .count();
    }

    /**
     * Check if user is within the D20 window after /roll
     */
    private boolean isWithinD20Window(UserCooldown cooldown) {
        if (cooldown == null || cooldown.getLastRollTime() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rollTime = cooldown.getLastRollTime();
        return java.time.Duration.between(rollTime, now).toMinutes() < D20_WINDOW_MINUTES;
    }

    /**
     * Handle the /d20 command
     */
    private void handleD20(SlashCommandInteractionEvent event) {
        logger.debug("handleD20 called for user {}", event.getUser().getName());
        Member member = event.getMember();
        if (member == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        String userId = event.getUser().getId();
        String guildId = event.getGuild().getId();
        logger.debug("D20 check: userId={}, guildId={}", userId, guildId);

        // Check if server has 3+ Epic/Legendary roles
        List<RoleInfo> gachaRoles = event.getGuild().getRoles().stream()
            .filter(role -> role.getName().toLowerCase().startsWith(GACHA_PREFIX))
            .map(this::parseRoleInfo)
            .filter(Objects::nonNull)
            .toList();

        long epicPlusCount = countEpicPlusRoles(gachaRoles);
        if (epicPlusCount < 3) {
            event.reply("🎲 The /d20 feature requires at least 3 Epic or Legendary roles to be configured.\n" +
                "Ask your server admin to add more high-tier roles!").setEphemeral(true).queue();
            return;
        }

        // Check cooldown and window
        Optional<UserCooldown> cooldownOpt = cooldownRepository.findByUserIdAndGuildId(userId, guildId);
        if (cooldownOpt.isEmpty()) {
            event.reply("⏳ You must use `/roll` first!\n" +
                "The `/d20` command is only available for " + D20_WINDOW_MINUTES + " minutes after using `/roll`.").setEphemeral(true).queue();
            return;
        }

        UserCooldown cooldown = cooldownOpt.get();

        // Check if within the D20 window first (better UX - tells user why they can't use d20)
        if (!isWithinD20Window(cooldown)) {
            event.reply("⏱️ The `/d20` window has expired!\n" +
                "You have " + D20_WINDOW_MINUTES + " minutes after using `/roll` to use `/d20`.").setEphemeral(true).queue();
            return;
        }

        // Check if already used d20 this cycle
        if (cooldown.isD20Used()) {
            event.reply("🎲 You've already used `/d20` for this roll!\n" +
                "Wait for your cooldown to reset, then use `/roll` to start a new cycle.").setEphemeral(true).queue();
            return;
        }

        // Roll the d20!
        int d20Roll = rollD20();

        // Mark d20 as used
        cooldown.setD20Used(true);

        // Handle special results
        if (d20Roll == 20) {
            // Nat 20 - Grant Epic+ buff
            cooldown.setGuaranteedEpicPlus(true);
            cooldownRepository.save(cooldown);

            // Show animated response
            showD20Animation(event, d20Roll, "nat20");
        } else if (d20Roll == 1) {
            // Nat 1 - Extend cooldown to 48 hours
            cooldown.setLastRollTime(LocalDateTime.now().minusHours(24).plusHours(48));
            cooldownRepository.save(cooldown);

            // Show animated response
            showD20Animation(event, d20Roll, "nat1");
        } else {
            // Normal roll
            cooldownRepository.save(cooldown);

            // Show animated response
            showD20Animation(event, d20Roll, "normal");
        }

        logger.info("User {} rolled d20={} in guild {}", event.getUser().getName(), d20Roll, event.getGuild().getName());
    }

    /**
     * Show animated d20 roll with GIF and progressive text reveal
     */
    private void showD20Animation(SlashCommandInteractionEvent event, int finalRoll, String resultType) {
        // Get GIF URL - use GitHub raw URL for development/testing, production URL from env var
        String gifUrl;
        String baseUrl = System.getenv("ADMIN_PANEL_URL");
        if (baseUrl == null || baseUrl.contains("localhost")) {
            // Development: use GitHub raw URL so Discord can fetch it
            gifUrl = "https://raw.githubusercontent.com/wraithfive/playbot/master/src/main/resources/static/images/d20-roll.gif";
        } else {
            // Production: use the hosted server URL
            gifUrl = baseUrl + "/images/d20-roll.gif";
        }

        // Generate random intermediate numbers (avoid consecutive duplicates and final roll for drama)
        int[] intermediateNumbers = new int[D20_ANIMATION_FRAMES];
        int previousNum = -1;
        for (int i = 0; i < D20_ANIMATION_FRAMES; i++) {
            int num;
            do {
                num = random.nextInt(20) + 1;
            } while (num == finalRoll || num == previousNum);
            intermediateNumbers[i] = num;
            previousNum = num;
        }

        // Send initial frame with first intermediate number
        logger.debug("D20: animation starting with GIF URL: {}", gifUrl);
        EmbedBuilder embed1 = new EmbedBuilder();
        embed1.setTitle("🎲 Rolling d20...");
        embed1.setImage(gifUrl);
        embed1.setDescription("Rolling... **" + intermediateNumbers[0] + "**");
        embed1.setColor(Color.LIGHT_GRAY);

        // Start the animation sequence
        event.replyEmbeds(embed1.build()).setEphemeral(true).queue(hook -> {
            // Show remaining intermediate frames (1-5) then final result
            showIntermediateFrame(hook, gifUrl, intermediateNumbers, 1, finalRoll, resultType);
        }, error -> {
            logger.error("D20: Failed to send initial frame: {}", error.getMessage());
            // Fallback: send simple text result if animation fails
            sendFallbackResult(event, finalRoll, resultType);
        });
    }

    /**
     * Recursively show intermediate frames of the d20 animation.
     *
     * This method is designed to be race-condition safe:
     * - Each frame schedules the next frame only in its success callback
     * - Recursive pattern ensures sequential execution (no parallel frames)
     * - JDA's queueAfter() is thread-safe and uses JDA's executor
     * - Error handling ensures fallback if any frame fails
     *
     * @param frameIndex Current frame index (1-5 for intermediate frames)
     */
    private void showIntermediateFrame(InteractionHook hook, String gifUrl, int[] intermediateNumbers,
                                      int frameIndex, int finalRoll, String resultType) {
        if (frameIndex >= intermediateNumbers.length) {
            // All intermediate frames shown, now show final result
            showFinalResult(hook, gifUrl, finalRoll, resultType);
            return;
        }

        // Show this intermediate frame
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎲 Rolling d20...");
        embed.setImage(gifUrl);
        embed.setDescription("Rolling... **" + intermediateNumbers[frameIndex] + "**");
        embed.setColor(Color.LIGHT_GRAY);

        hook.editOriginalEmbeds(embed.build()).queueAfter(D20_FRAME_DELAY_MS, TimeUnit.MILLISECONDS,
            success -> {
                logger.debug("D20: frame {} sent with number {}", frameIndex + 1, intermediateNumbers[frameIndex]);
                // Recursively schedule the next frame
                showIntermediateFrame(hook, gifUrl, intermediateNumbers, frameIndex + 1, finalRoll, resultType);
            },
            error -> {
                logger.error("D20: Failed to send frame {}: {}", frameIndex + 1, error.getMessage());
                // Skip to final result if intermediate frame fails
                showFinalResult(hook, gifUrl, finalRoll, resultType);
            }
        );
    }

    /**
     * Show the final d20 result with appropriate formatting
     */
    private void showFinalResult(InteractionHook hook, String gifUrl, int finalRoll, String resultType) {
        EmbedBuilder finalEmbed = new EmbedBuilder();

        switch (resultType) {
            case "nat20" -> {
                finalEmbed.setTitle("🎲 Natural 20! 🌟");
                finalEmbed.setDescription("**You rolled: " + finalRoll + "**\n\n" +
                    "✨ **Lucky Streak!** Your next roll is guaranteed to be Epic or Legendary!");
                finalEmbed.setColor(Color.YELLOW);
            }
            case "nat1" -> {
                finalEmbed.setTitle("🎲 Critical Failure! 💀");
                finalEmbed.setDescription("**You rolled: " + finalRoll + "**\n\n" +
                    "⏰ Your cooldown has been extended to **48 hours**!");
                finalEmbed.setColor(Color.RED);
            }
            default -> {
                finalEmbed.setTitle("🎲 d20 Roll");
                finalEmbed.setDescription("**You rolled: " + finalRoll + "**\n\nNo special effect.");
                finalEmbed.setColor(Color.GRAY);
            }
        }

        finalEmbed.setImage(gifUrl);

        hook.editOriginalEmbeds(finalEmbed.build()).queueAfter(D20_FRAME_DELAY_MS, TimeUnit.MILLISECONDS,
            success -> logger.debug("D20: final result ({}) sent successfully", resultType),
            error -> {
                logger.error("D20: Failed to send final result: {}", error.getMessage());
                // Try sending as plain text without embed
                String message = switch (resultType) {
                    case "nat20" -> String.format("🎲 **Natural 20!** You rolled: %d\n\n" +
                        "✨ **Lucky Streak!** Your next roll is guaranteed to be Epic or Legendary!", finalRoll);
                    case "nat1" -> String.format("🎲 **Critical Failure!** You rolled: %d\n\n" +
                        "⏰ Your cooldown has been extended to **48 hours**!", finalRoll);
                    default -> String.format("🎲 You rolled: **%d**\n\nNo special effect.", finalRoll);
                };
                hook.editOriginal(message).queue();
            }
        );
    }

    /**
     * Roll a d20 (1-20). Protected so it can be mocked in tests.
     */
    protected int rollD20() {
        return random.nextInt(20) + 1;
    }

    /**
     * Send a simple text fallback result if animation fails
     */
    private void sendFallbackResult(SlashCommandInteractionEvent event, int finalRoll, String resultType) {
        String message = switch (resultType) {
            case "nat20" -> String.format("🎲 **Natural 20!** You rolled: %d\n\n" +
                "✨ **Lucky Streak!** Your next roll is guaranteed to be Epic or Legendary!", finalRoll);
            case "nat1" -> String.format("🎲 **Critical Failure!** You rolled: %d\n\n" +
                "⏰ Your cooldown has been extended to **48 hours**!", finalRoll);
            default -> String.format("🎲 You rolled: **%d**\n\nNo special effect.", finalRoll);
        };

        event.reply(message).setEphemeral(true).queue(
            success -> logger.debug("D20: Fallback result sent successfully"),
            error -> logger.error("D20: Failed to send even fallback result: {}", error.getMessage())
        );
    }

    // Inner classes

    private record RoleInfo(String roleId, String displayName, Rarity rarity) {}

    private enum Rarity {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
    }
}
