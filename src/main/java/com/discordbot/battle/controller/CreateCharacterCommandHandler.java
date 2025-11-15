package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.util.CharacterCreationUIBuilder;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles the /create-character slash command.
 * Displays an interactive embed with dropdowns and buttons for character creation.
 */
@Component
public class CreateCharacterCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateCharacterCommandHandler.class);

    private final BattleProperties battleProperties;
    private final PlayerCharacterRepository repository;
    private final CharacterCreationUIBuilder uiBuilder;

    public CreateCharacterCommandHandler(BattleProperties battleProperties, 
                                        PlayerCharacterRepository repository,
                                        CharacterCreationUIBuilder uiBuilder) {
        this.battleProperties = battleProperties;
        this.repository = repository;
        this.uiBuilder = uiBuilder;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "create-character".equals(commandName);
    }

    /**
     * Handles character creation: displays interactive embed with class/race selection menus
     * and point-buy rules explanation.
     */
    @Override
    public void handle(SlashCommandInteractionEvent event) {
        try {
            // Extract Discord context
            String userId = event.getUser().getId();
            String guildId = event.getGuild().getId();
            
            // Check if character already exists
            Optional<PlayerCharacter> existing = repository.findByUserIdAndGuildId(userId, guildId);
            if (existing.isPresent()) {
                logger.debug("Character creation attempted for userId={} in guildId={} but character already exists", userId, guildId);
                event.reply("❌ You already have a character in this server!")
                    .setEphemeral(true)
                    .queue();
                return;
            }
            
            // Show interactive character creation embed using the UI builder
            var message = uiBuilder.buildCharacterCreationMessage(userId, true);
            event.reply(message)
                .setEphemeral(true)
                .queue();

        } catch (Exception e) {
            logger.error("Error handling create-character command for user={}", event.getUser().getId(), e);
            event.reply("❌ An error occurred while starting character creation.")
                .setEphemeral(true)
                .queue();
        }
    }
}
