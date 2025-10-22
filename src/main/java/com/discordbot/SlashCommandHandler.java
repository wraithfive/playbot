package com.discordbot;

import com.discordbot.entity.UserCooldown;
import com.discordbot.repository.UserCooldownRepository;
import com.discordbot.web.service.GuildsCache;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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
    private final GuildsCache guildsCache;
    private final WebSocketNotificationService webSocketNotificationService;
    private final Random random = new Random();

    @Autowired
    public SlashCommandHandler(
            UserCooldownRepository cooldownRepository, 
            GuildsCache guildsCache,
            WebSocketNotificationService webSocketNotificationService) {
        this.cooldownRepository = cooldownRepository;
        this.guildsCache = guildsCache;
        this.webSocketNotificationService = webSocketNotificationService;
        logger.info("SlashCommandHandler initialized with database persistence and WebSocket notifications");
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
            Commands.slash("testroll", "Test roll without cooldown (admin only)"),
            Commands.slash("mycolor", "Check your current gacha role"),
            Commands.slash("colors", "View all available gacha roles"),
            Commands.slash("help", "Show help information")
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
            Commands.slash("testroll", "Test roll without cooldown (admin only)"),
            Commands.slash("mycolor", "Check your current gacha role"),
            Commands.slash("colors", "View all available gacha roles"),
            Commands.slash("help", "Show help information")
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
            case "testroll" -> handleTestRoll(event);
            case "mycolor" -> handleMyColor(event);
            case "colors" -> handleColors(event);
            case "help" -> handleHelp(event);
            default -> event.reply("Unknown command!").setEphemeral(true).queue();
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

        // Roll a random role based on rarity weights
        RoleInfo rolledRole = rollRandomRole(gachaRoles);
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
            embed.setDescription(String.format("You rolled: **%s** %s\n\nRarity: **%s**",
                rolledRole.displayName,
                rarityEmoji,
                rolledRole.rarity != null ? rolledRole.rarity.name() : "Unknown"));

            if (rolledRole.rarity != null) {
                embed.addField("Drop Rate", String.format("%.1f%%", getRarityWeight(rolledRole.rarity) * 100), true);
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

    // Inner classes

    private record RoleInfo(String roleId, String displayName, Rarity rarity) {}

    private enum Rarity {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
    }
}
