package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles the /create-character slash command.
 * Displays an interactive modal for character creation.
 */
@Component
public class CreateCharacterCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateCharacterCommandHandler.class);

    private final BattleProperties battleProperties;
    private final PlayerCharacterRepository repository;

    public CreateCharacterCommandHandler(BattleProperties battleProperties, 
                                        PlayerCharacterRepository repository) {
        this.battleProperties = battleProperties;
        this.repository = repository;
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
            
            // Show interactive character creation embed
            showCharacterCreationEmbed(event);

        } catch (Exception e) {
            logger.error("Error handling create-character command for user={}", event.getUser().getId(), e);
            event.reply("❌ An error occurred while starting character creation.")
                .setEphemeral(true)
                .queue();
        }
    }
    
    /**
     * Displays the initial character creation modal with all fields and explanations.
     */
    private void showCharacterCreationEmbed(SlashCommandInteractionEvent event) {
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        
        // Build text inputs for character creation
        TextInput classInput = TextInput.create("class", TextInputStyle.SHORT)
            .setPlaceholder("Warrior, Rogue, Mage, or Cleric")
            .setRequired(true)
            .setMinLength(3)
            .setMaxLength(10)
            .build();
        
        TextInput raceInput = TextInput.create("race", TextInputStyle.SHORT)
            .setPlaceholder("Human, Elf, Dwarf, or Halfling")
            .setRequired(true)
            .setMinLength(3)
            .setMaxLength(10)
            .build();
        
        TextInput statsInput = TextInput.create("stats", TextInputStyle.SHORT)
            .setPlaceholder("Example: 15 14 13 12 10 8")
            .setRequired(true)
            .setValue("10 10 10 10 10 10")
            .setMinLength(11)
            .setMaxLength(23)
            .build();
        
        // Create modal with informative title - wrap inputs in Labels
        Modal modal = Modal.create("char-create:" + event.getUser().getId(), "⚔️ Create Your Character")
            .addComponents(
                Label.of("Class (Warrior/Rogue/Mage/Cleric)", classInput),
                Label.of("Race (Human/Elf/Dwarf/Halfling)", raceInput),
                Label.of(String.format("Stats (%d pts: 8-15 range)", pointBuy.getTotalPoints()), statsInput)
            )
            .build();
        
        // Reply with the modal
        event.replyModal(modal).queue();
        
        // User can reference /battle-help for detailed rules explanation
    }
}
