package com.discordbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class ColorGachaHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ColorGachaHandler.class);
    private static final Logger activityLogger = LoggerFactory.getLogger("com.discordbot.activity");

    private static final String PREFIX = "!";

    private static final String GACHA_PREFIX = "gacha:";

    // Store user roll data: userId -> last roll date
    private final Map<String, LocalDate> userRollHistory = new HashMap<>();

    private final Random random = new Random();

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("Playbot Gacha Handler initialized and ready");
        logger.info("Logged in as: {}", event.getJDA().getSelfUser().getName());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw();
        if (!message.startsWith(PREFIX)) {
            return;
        }

        String[] parts = message.substring(PREFIX.length()).split("\\s+");
        String command = parts[0].toLowerCase();

        logger.debug("Command received: {} from user: {} in guild: {}",
            command, event.getAuthor().getName(),
            event.isFromGuild() ? event.getGuild().getName() : "DM");

        switch (command) {
            case "roll":
                handleRoll(event, false);
                break;

            case "testroll":
                handleTestRoll(event);
                break;

            case "colors":
                handleColorList(event);
                break;

            case "mycolor":
                handleMyColor(event);
                break;

            case "help":
                handleHelp(event);
                break;

            default:
                logger.debug("Unknown command: {} from user: {}", command, event.getAuthor().getName());
                event.getChannel().sendMessage("Unknown command. Type `!help` for commands.").queue();
        }
    }

    private void handleRoll(MessageReceivedEvent event, boolean skipDailyCheck) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("This command can only be used in a server!").queue();
            return;
        }

        String userId = event.getAuthor().getId();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        // Check if user already rolled today (unless skipDailyCheck is true)
        if (!skipDailyCheck && userRollHistory.containsKey(userId)) {
            LocalDate lastRoll = userRollHistory.get(userId);
            if (lastRoll.equals(today)) {
                event.getChannel().sendMessage("You've already rolled today! Come back tomorrow for a new color!").queue();
                return;
            }
        }

        Member member = event.getMember();
        if (member == null) {
            return;
        }

        // Get all gacha roles from the server
        List<Role> gachaRoles = getGachaRoles(event);
        logger.debug("Found {} gacha roles in guild: {}", gachaRoles.size(), event.getGuild().getName());

        if (gachaRoles.isEmpty()) {
            logger.warn("No gacha roles found in guild: {}", event.getGuild().getName());
            event.getChannel().sendMessage("No gacha roles found! Server admins need to create roles starting with `" + GACHA_PREFIX + "`").queue();
            return;
        }

        // Remove old gacha role from user
        List<Role> userRoles = member.getRoles();
        for (Role role : userRoles) {
            String roleName = role.getName().toLowerCase();
            if (roleName.startsWith(GACHA_PREFIX)) {
                logger.info("Removing old gacha role: {} from user: {}", role.getName(), member.getUser().getName());
                event.getGuild().removeRoleFromMember(member, role).queue();
            }
        }

        // Pick a weighted random gacha role
        Role randomRole = pickWeightedRole(gachaRoles);
        RoleInfo roleInfo = parseRoleInfo(randomRole);

        // Assign the new role
        logger.info("Assigning gacha role: {} to user: {} in guild: {}",
            randomRole.getName(), member.getUser().getName(), event.getGuild().getName());

        event.getGuild().addRoleToMember(member, randomRole).queue(
            success -> {
                logger.info("Successfully assigned role: {} to user: {}", randomRole.getName(), member.getUser().getName());
                activityLogger.info("ROLL | User: {} | Guild: {} | Color: {} | Rarity: {}",
                    member.getUser().getName(), event.getGuild().getName(),
                    roleInfo.displayName, roleInfo.rarity != null ? roleInfo.rarity.name() : "NONE");

                userRollHistory.put(userId, today);

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("🎨 Color Gacha Roll!");
                embed.setColor(randomRole.getColor());
                embed.setDescription("**" + event.getAuthor().getName() + "** rolled...");
                embed.addField("Color", roleInfo.displayName, true);
                if (roleInfo.rarity != null) {
                    embed.addField("Rarity", roleInfo.rarity.getEmoji() + " " + roleInfo.rarity.name(), true);
                }
                embed.addField("", "Your name color has been updated!", false);
                embed.setFooter("Come back tomorrow to roll again!");
                embed.setTimestamp(Instant.now());

                event.getChannel().sendMessageEmbeds(embed.build()).queue();
            },
            error -> {
                logger.error("Failed to assign role: {} to user: {} in guild: {}. Error: {}",
                    randomRole.getName(), member.getUser().getName(), event.getGuild().getName(), error.getMessage(), error);
                event.getChannel().sendMessage("Error assigning color role. Make sure the bot has proper permissions!").queue();
            }
        );
    }

    private void handleTestRoll(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("This command can only be used in a server!").queue();
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            return;
        }

        // Check if user has admin or moderator permissions
        if (!isAdminOrMod(member)) {
            logger.warn("User {} attempted to use testroll without permissions in guild: {}",
                member.getUser().getName(), event.getGuild().getName());
            event.getChannel().sendMessage("This command is only available to admins and moderators!").queue();
            return;
        }

        logger.info("Admin/Mod {} using testroll command in guild: {}",
            member.getUser().getName(), event.getGuild().getName());

        // Call handleRoll with skipDailyCheck = true
        handleRoll(event, true);
    }

    private boolean isAdminOrMod(Member member) {
        return member.hasPermission(Permission.ADMINISTRATOR) ||
               member.hasPermission(Permission.MANAGE_SERVER) ||
               member.hasPermission(Permission.MODERATE_MEMBERS);
    }

    private void handleColorList(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("This command can only be used in a server!").queue();
            return;
        }

        List<Role> gachaRoles = getGachaRoles(event);

        if (gachaRoles.isEmpty()) {
            event.getChannel().sendMessage("No gacha roles found! Server admins need to create roles starting with `" + GACHA_PREFIX + "`").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎨 Available Colors");
        embed.setColor(Color.CYAN);
        embed.setDescription("Here are all the colors you can roll! (" + gachaRoles.size() + " total)");

        // Group by rarity
        Map<Rarity, List<String>> colorsByRarity = new EnumMap<>(Rarity.class);
        List<String> noRarity = new ArrayList<>();

        for (Role role : gachaRoles) {
            RoleInfo roleInfo = parseRoleInfo(role);
            if (roleInfo.rarity != null) {
                colorsByRarity.computeIfAbsent(roleInfo.rarity, k -> new ArrayList<>()).add(roleInfo.displayName);
            } else {
                noRarity.add(roleInfo.displayName);
            }
        }

        // Display by rarity
        for (Rarity rarity : Rarity.values()) {
            List<String> colors = colorsByRarity.get(rarity);
            if (colors != null && !colors.isEmpty()) {
                embed.addField(
                    rarity.getEmoji() + " " + rarity.name() + " (" + rarity.getChance() + ")",
                    String.join(", ", colors),
                    false
                );
            }
        }

        // Display no-rarity colors
        if (!noRarity.isEmpty()) {
            embed.addField("No Rarity (equal chance)", String.join(", ", noRarity), false);
        }

        embed.setFooter("Format: gacha:rarity:ColorName or gacha:ColorName");
        embed.setTimestamp(Instant.now());

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private List<Role> getGachaRoles(MessageReceivedEvent event) {
        List<Role> gachaRoles = new ArrayList<>();
        for (Role role : event.getGuild().getRoles()) {
            if (role.getName().toLowerCase().startsWith(GACHA_PREFIX)) {
                gachaRoles.add(role);
            }
        }
        return gachaRoles;
    }

    private void handleMyColor(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessage("This command can only be used in a server!").queue();
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            return;
        }

        // Find gacha role on user
        Role gachaRole = null;
        for (Role role : member.getRoles()) {
            if (role.getName().toLowerCase().startsWith(GACHA_PREFIX)) {
                gachaRole = role;
                break;
            }
        }

        if (gachaRole == null) {
            event.getChannel().sendMessage("You haven't rolled a color yet! Use `!roll` to get started.").queue();
            return;
        }

        RoleInfo roleInfo = parseRoleInfo(gachaRole);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Your Current Color");
        embed.setColor(gachaRole.getColor());
        embed.addField("Color", roleInfo.displayName, true);
        if (roleInfo.rarity != null) {
            embed.addField("Rarity", roleInfo.rarity.getEmoji() + " " + roleInfo.rarity.name(), true);
        }

        String userId = event.getAuthor().getId();
        LocalDate lastRoll = userRollHistory.get(userId);
        if (lastRoll != null) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault());
            if (lastRoll.equals(today)) {
                embed.setFooter("You can roll again tomorrow!");
            } else {
                embed.setFooter("You can roll again now! Use !roll");
            }
        }

        embed.setTimestamp(Instant.now());
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleHelp(MessageReceivedEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎨 Playbot - Help");
        embed.setColor(Color.MAGENTA);
        embed.setDescription("Roll for a random color every day to change your name color!\n\nServer admins can create gacha roles with format:\n`gacha:rarity:ColorName` or `gacha:ColorName`");
        embed.addField("!roll", "Roll for a random color (once per day)", false);
        embed.addField("!testroll", "Test roll command (admins/mods only)", false);
        embed.addField("!mycolor", "Check your current color", false);
        embed.addField("!colors", "View all available colors and their rarities", false);
        embed.addField("!help", "Show this help message", false);
        embed.addField("", "**Valid Rarities:** common, uncommon, rare, epic, legendary", false);

        // Add admin panel and legal links
        String adminPanelUrl = System.getProperty("ADMIN_PANEL_URL", "http://localhost:8080");
        embed.addField("📋 Links",
            String.format("**Admin Panel:** %s\n**Privacy Policy:** %s/privacy\n**Terms of Service:** %s/terms",
                adminPanelUrl, adminPanelUrl, adminPanelUrl),
            false);

        embed.setFooter("Good luck with your rolls! By using this bot, you agree to our Terms of Service.");
        embed.setTimestamp(Instant.now());

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    // Parse role info from role name format: gacha:rarity:ColorName or gacha:ColorName
    private RoleInfo parseRoleInfo(Role role) {
        String fullName = role.getName().toLowerCase();
        String afterPrefix = fullName.substring(GACHA_PREFIX.length());

        String[] parts = afterPrefix.split(":");

        if (parts.length >= 2) {
            // Format: gacha:rarity:ColorName
            String rarityStr = parts[0].toUpperCase();
            String colorName = afterPrefix.substring(parts[0].length() + 1); // Get original case

            try {
                Rarity rarity = Rarity.valueOf(rarityStr);
                return new RoleInfo(colorName, rarity);
            } catch (IllegalArgumentException e) {
                // Invalid rarity, treat whole thing as color name
                return new RoleInfo(afterPrefix, null);
            }
        } else {
            // Format: gacha:ColorName
            return new RoleInfo(afterPrefix, null);
        }
    }

    // Pick a weighted random role based on rarity
    private Role pickWeightedRole(List<Role> roles) {
        // Calculate total weight
        double totalWeight = 0;
        for (Role role : roles) {
            RoleInfo info = parseRoleInfo(role);
            totalWeight += (info.rarity != null) ? info.rarity.getWeight() : 1.0; // Default weight = 1
        }

        // Pick random value
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;

        for (Role role : roles) {
            RoleInfo info = parseRoleInfo(role);
            double weight = (info.rarity != null) ? info.rarity.getWeight() : 1.0;
            cumulative += weight;

            if (roll < cumulative) {
                return role;
            }
        }

        return roles.get(0); // Fallback
    }

    // Java 25: Using records for immutable data carriers
    private record RoleInfo(String displayName, Rarity rarity) {}

    // Java 25: Modern enum with record-style accessors
    private enum Rarity {
        COMMON(10, "⚪", "70%"),
        UNCOMMON(4, "🟢", "20%"),
        RARE(2.33, "🔵", "7%"),
        EPIC(1.25, "🟣", "2.5%"),
        LEGENDARY(0.25, "🟡", "0.5%");

        private final double weight;
        private final String emoji;
        private final String chance;

        Rarity(double weight, String emoji, String chance) {
            this.weight = weight;
            this.emoji = emoji;
            this.chance = chance;
        }

        // Java 25: Simplified accessors
        double getWeight() { return weight; }
        String getEmoji() { return emoji; }
        String getChance() { return chance; }
    }
}
