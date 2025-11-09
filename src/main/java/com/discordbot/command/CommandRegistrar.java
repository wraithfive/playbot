package com.discordbot.command;

import com.discordbot.battle.config.BattleProperties;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized, unified registrar for all slash commands.
 *
 * Registers guild-scoped commands on GuildReady and GuildJoin in a single
 * updateCommands() call to avoid race conditions and overwrite issues.
 */
@Component
public class CommandRegistrar extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandRegistrar.class);

    private final BattleProperties battleProperties;

    public CommandRegistrar(BattleProperties battleProperties) {
        this.battleProperties = battleProperties;
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        registerForGuild(event.getGuild().getId(), event);
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        registerForGuild(event.getGuild().getId(), event);
    }

    private void registerForGuild(String guildId, Object sourceEvent) {
        try {
            List<CommandData> commands = new ArrayList<>();

            // Core gacha and utility commands
            commands.add(Commands.slash("roll", "Roll for a random gacha role (once per day)"));
            commands.add(Commands.slash("d20", "Roll a d20 for bonus/penalty (60 min after /roll)"));
            commands.add(Commands.slash("testroll", "Test roll without cooldown (admin only)"));
            commands.add(Commands.slash("mycolor", "Check your current gacha role"));
            commands.add(Commands.slash("colors", "View all available gacha roles"));
            commands.add(Commands.slash("help", "Show help information"));
            commands.add(
                Commands.slash("qotd-submit", "Suggest a Question of the Day for admins to review")
                    .addOption(OptionType.STRING, "question", "Your question (max 300 chars)", true)
                    .addOption(OptionType.STRING, "stream", "Target stream (optional)", false, true)
            );

            // Battle commands (feature-flagged)
            if (battleProperties != null && battleProperties.isEnabled()) {
                commands.add(Commands.slash("battle-help", "Show information about the battle system"));
                commands.add(Commands.slash("create-character", "Create a new battle character with D&D 5e stats"));
                commands.add(
                    Commands.slash("duel", "Challenge another player to a duel")
                        .addOption(OptionType.USER, "opponent", "The user you want to challenge", true)
                );
                // Abilities management
                commands.add(
                    Commands.slash("abilities", "View and learn abilities interactively")
                        .addOption(OptionType.STRING, "filter", "Filter: available | learned (optional)", false)
                        .addOption(OptionType.STRING, "type", "Ability type: TALENT | SKILL | SPELL (optional)", false)
                );
            }

            // One atomic registration per guild
            if (sourceEvent instanceof GuildReadyEvent gre) {
                gre.getGuild().updateCommands().addCommands(commands).queue(
                    success -> logger.info("Registered {} commands for guild (ready): {}", commands.size(), gre.getGuild().getName()),
                    error -> logger.error("Failed to register commands on guild ready for {}: {}", gre.getGuild().getName(), error.getMessage())
                );
            } else if (sourceEvent instanceof GuildJoinEvent gje) {
                gje.getGuild().updateCommands().addCommands(commands).queue(
                    success -> logger.info("Registered {} commands for newly joined guild: {}", commands.size(), gje.getGuild().getName()),
                    error -> logger.error("Failed to register commands on guild join for {}: {}", gje.getGuild().getName(), error.getMessage())
                );
            }
        } catch (Exception e) {
            logger.error("Command registration failed for guild {}: {}", guildId, e.getMessage(), e);
        }
    }
}
