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
import java.util.stream.Collectors;

@Component
public class SlashCommandHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SlashCommandHandler.class);

    private static final String GACHA_PREFIX = "gacha:";

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

        // Check if user has Epic+ buff from nat 20
        boolean hasEpicPlusBuff = false;
        if (!isTest) {
            Optional<UserCooldown> cooldownOpt = cooldownRepository.findByUserIdAndGuildId(userId, guildId);
            if (cooldownOpt.isPresent() && cooldownOpt.get().isGuaranteedEpicPlus()) {
                hasEpicPlusBuff = true;
            }
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

            // Update cooldown in database
            if (!isTest) {
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
                logger.debug("Updated cooldown for user {} in guild {}", userId, guildId);
            }

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

        // Check for d20 buffs/status
        Optional<UserCooldown> cooldownOpt = cooldownRepository.findByUserIdAndGuildId(userId, guildId);
        if (cooldownOpt.isPresent()) {
            UserCooldown cooldown = cooldownOpt.get();

            // Show Epic+ buff if active
            if (cooldown.isGuaranteedEpicPlus()) {
                embed.addField("🎲 Lucky Streak Active!",
                    "Your next roll is guaranteed to be Epic or Legendary!",
                    false);
            }

            // Show d20 window if active and not used
            if (!cooldown.isD20Used() && isWithinD20Window(cooldown)) {
                long minutesLeft = 60 - java.time.Duration.between(cooldown.getLastRollTime(), LocalDateTime.now()).toMinutes();
                embed.addField("🎲 /d20 Available",
                    String.format("Use `/d20` within %d minutes for a bonus or penalty!", minutesLeft),
                    false);
            }

            // Show extended cooldown if applicable
            LocalDate today = LocalDate.now(ZoneId.systemDefault());
            LocalDate lastRoll = cooldown.getLastRollTime().toLocalDate();
            if (lastRoll.equals(today)) {
                long hoursUntilRoll = java.time.Duration.between(LocalDateTime.now(),
                    cooldown.getLastRollTime().plusHours(24)).toHours();

                if (hoursUntilRoll > 24) {
                    // Extended cooldown from nat 1
                    embed.addField("⏳ Cooldown (Critical Failure)",
                        String.format("%d hours remaining", hoursUntilRoll),
                        false);
                }
            }
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
     * Check if user is within 60-minute window after /roll
     */
    private boolean isWithinD20Window(UserCooldown cooldown) {
        if (cooldown == null || cooldown.getLastRollTime() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rollTime = cooldown.getLastRollTime();
        return java.time.Duration.between(rollTime, now).toMinutes() < 60;
    }

    /**
     * Handle the /d20 command
     */
    private void handleD20(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        String userId = event.getUser().getId();
        String guildId = event.getGuild().getId();

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
                "The `/d20` command is only available for 60 minutes after using `/roll`.").setEphemeral(true).queue();
            return;
        }

        UserCooldown cooldown = cooldownOpt.get();

        // Check if already used d20 this cycle
        if (cooldown.isD20Used()) {
            event.reply("🎲 You've already used `/d20` for this roll!\n" +
                "Wait for your cooldown to reset, then use `/roll` to start a new cycle.").setEphemeral(true).queue();
            return;
        }

        // Check if within 60-minute window
        if (!isWithinD20Window(cooldown)) {
            event.reply("⏱️ The `/d20` window has expired!\n" +
                "You have 60 minutes after using `/roll` to use `/d20`.").setEphemeral(true).queue();
            return;
        }

        // Roll the d20!
        int d20Roll = random.nextInt(20) + 1;

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
        // Get server URL for GIF (use environment variable or default to localhost)
        String baseUrl = System.getenv("ADMIN_PANEL_URL");
        if (baseUrl == null) baseUrl = "http://localhost:8080";
        String gifUrl = baseUrl + "/images/d20-roll.gif";

        // Generate random intermediate numbers (different from final roll for drama)
        int tempFrame1 = random.nextInt(20) + 1;
        while (tempFrame1 == finalRoll) {
            tempFrame1 = random.nextInt(20) + 1;
        }
        final int frame1Number = tempFrame1;

        int tempFrame2 = random.nextInt(20) + 1;
        while (tempFrame2 == finalRoll) {
            tempFrame2 = random.nextInt(20) + 1;
        }
        final int frame2Number = tempFrame2;

        // Frame 1: Initial roll with GIF
        EmbedBuilder embed1 = new EmbedBuilder();
        embed1.setTitle("🎲 Rolling d20...");
        embed1.setImage(gifUrl);
        embed1.setDescription("Rolling... **" + frame1Number + "**");
        embed1.setColor(Color.LIGHT_GRAY);

        event.replyEmbeds(embed1.build()).setEphemeral(true).queue(hook -> {
            try {
                // Frame 2: Second number
                Thread.sleep(600);
                EmbedBuilder embed2 = new EmbedBuilder();
                embed2.setTitle("🎲 Rolling d20...");
                embed2.setImage(gifUrl);
                embed2.setDescription("Rolling... **" + frame2Number + "**");
                embed2.setColor(Color.LIGHT_GRAY);
                hook.editOriginalEmbeds(embed2.build()).queue();

                // Frame 3: Final result
                Thread.sleep(600);
                EmbedBuilder embed3 = new EmbedBuilder();

                if (resultType.equals("nat20")) {
                    embed3.setTitle("🎲 NAT 20!");
                    embed3.setDescription("Rolling... **" + finalRoll + "!**\n\n" +
                        "🎉 **Lucky Streak!**\n" +
                        "Your next roll is guaranteed to be Epic or Legendary!");
                    embed3.setColor(Color.YELLOW);
                } else if (resultType.equals("nat1")) {
                    embed3.setTitle("🎲 NAT 1!");
                    embed3.setDescription("Rolling... **" + finalRoll + "!**\n\n" +
                        "💀 **Critical Failure!**\n" +
                        "Your cooldown has been extended to 48 hours.");
                    embed3.setColor(Color.RED);
                } else {
                    embed3.setTitle("🎲 d20 Result");
                    embed3.setDescription("Rolling... **" + finalRoll + "**\n\n" +
                        "No special effect this time. Better luck next roll!");
                    embed3.setColor(Color.GRAY);
                }

                embed3.setImage(gifUrl);
                embed3.setTimestamp(Instant.now());
                hook.editOriginalEmbeds(embed3.build()).queue();

            } catch (InterruptedException e) {
                logger.error("D20 animation interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        });
    }

    // Inner classes

    private record RoleInfo(String roleId, String displayName, Rarity rarity) {}

    private enum Rarity {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
    }
}
