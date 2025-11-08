package com.discordbot.command;

import com.discordbot.SlashCommandHandler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Adapter that wraps the legacy SlashCommandHandler to work with CommandRouter.
 * This allows gradual migration of commands to dedicated handlers.
 * 
 * Once all commands are migrated, this adapter can be removed.
 */
@Component
public class LegacySlashCommandAdapter implements CommandHandler {

    private static final Set<String> HANDLED_COMMANDS = Set.of(
        "roll",
        "d20",
        "testroll",
        "mycolor",
        "colors",
        "help",
        "qotd-submit"
    );

    private final SlashCommandHandler legacyHandler;

    public LegacySlashCommandAdapter(SlashCommandHandler legacyHandler) {
        this.legacyHandler = legacyHandler;
    }

    @Override
    public boolean canHandle(String commandName) {
        return HANDLED_COMMANDS.contains(commandName);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        // Delegate to the legacy handler's onSlashCommandInteraction
        legacyHandler.onSlashCommandInteraction(event);
    }
}
