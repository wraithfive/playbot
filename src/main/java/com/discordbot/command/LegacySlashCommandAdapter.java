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

    /**
     * Commands handled by the legacy SlashCommandHandler.
     * 
     * These commands remain in the legacy adapter during gradual migration to dedicated handlers.
     * Migration strategy:
     * 1. New commands use dedicated CommandHandler implementations
     * 2. Legacy commands here are gradually refactored and moved to dedicated handlers
     * 3. Once a command is migrated, remove it from this set and update canHandle() accordingly
     * 4. When this set is empty, remove the entire LegacySlashCommandAdapter
     */
    private static final Set<String> HANDLED_COMMANDS = Set.of(
        "roll",      // Gacha roll - part of color gacha system
        "d20",       // D20 risk/reward mechanic
        "testroll",  // Test command for development
        "mycolor",   // View user's current gacha color
        "colors",    // List available colors
        "help",      // General help command
        "qotd-submit" // Question of the Day submission
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
