package com.discordbot.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Routes slash command interactions to the appropriate command handler.
 * This prevents multiple handlers from trying to respond to the same command,
 * which causes "interaction already acknowledged" errors.
 * 
 * Handlers implement CommandHandler interface and register their supported commands.
 */
@Component
public class CommandRouter extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandRouter.class);

    private final List<CommandHandler> handlers;

    public CommandRouter(@Lazy List<CommandHandler> handlers) {
        this.handlers = handlers;
        logger.info("CommandRouter initialized (handlers will be lazily resolved)");
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        // Find the first handler that can handle this command
        for (CommandHandler handler : handlers) {
            if (handler.canHandle(commandName)) {
                try {
                    handler.handle(event);
                    return; // Command handled, stop processing
                } catch (Exception e) {
                    logger.error("Error handling command '{}' with handler {}: {}", 
                        commandName, handler.getClass().getSimpleName(), e.getMessage(), e);
                    
                    // Only reply with error if interaction hasn't been acknowledged
                    if (!event.isAcknowledged()) {
                        event.reply("❌ An error occurred while processing your command.")
                            .setEphemeral(true)
                            .queue();
                    }
                    return;
                }
            }
        }

        // No handler found for this command
        logger.warn("No handler found for command: {}", commandName);
        if (!event.isAcknowledged()) {
            event.reply("❌ Unknown command: " + commandName)
                .setEphemeral(true)
                .queue();
        }
    }
}
