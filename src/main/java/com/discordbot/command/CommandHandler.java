package com.discordbot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Interface for slash command handlers.
 * Each handler declares which commands it can handle and provides the handling logic.
 */
public interface CommandHandler {

    /**
     * Check if this handler can handle the given command.
     * 
     * @param commandName The name of the slash command
     * @return true if this handler should process the command
     */
    boolean canHandle(String commandName);

    /**
     * Handle the slash command interaction.
     * This method should acknowledge/reply to the interaction.
     * 
     * @param event The slash command interaction event
     */
    void handle(SlashCommandInteractionEvent event);
}
