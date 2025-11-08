package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.CharacterConstants;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.CharacterValidationService;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.Optional;

/**
 * Handles the /create-character slash command.
 * Validates ability scores using point-buy rules, saves to database, and creates a character sheet embed.
 */
@Component
public class CreateCharacterCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateCharacterCommandHandler.class);

    private final BattleProperties battleProperties;
    private final CharacterValidationService validationService;
    private final PlayerCharacterRepository repository;

    public CreateCharacterCommandHandler(BattleProperties battleProperties, 
                                        CharacterValidationService validationService,
                                        PlayerCharacterRepository repository) {
        this.battleProperties = battleProperties;
        this.validationService = validationService;
        this.repository = repository;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "create-character".equals(commandName);
    }

    /**
     * Handles character creation: validates ability scores, checks for duplicates, 
     * saves to database, and sends character sheet embed.
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
            
            // Extract options
            String characterClass = event.getOption("class").getAsString();
            String race = event.getOption("race").getAsString();
            int strength = event.getOption("strength").getAsInt();
            int dexterity = event.getOption("dexterity").getAsInt();
            int constitution = event.getOption("constitution").getAsInt();
            int intelligence = event.getOption("intelligence").getAsInt();
            int wisdom = event.getOption("wisdom").getAsInt();
            int charisma = event.getOption("charisma").getAsInt();

            // Create character entity
            PlayerCharacter character = new PlayerCharacter(
                userId, guildId,
                characterClass, race, 
                strength, dexterity, constitution, 
                intelligence, wisdom, charisma
            );

            // Validate
            if (!validationService.isValid(character)) {
                logger.debug("Character validation failed for userId={}: invalid stats or class/race", userId);
                sendErrorMessage(event, character);
                return;
            }

            // Save to database
            try {
                PlayerCharacter saved = repository.save(character);
                logger.info("Character created for userId={} in guildId={} - class={}, race={}", 
                    userId, guildId, characterClass, race);
                
                // Success - send character sheet embed
                sendSuccessEmbed(event, saved);
            } catch (DataIntegrityViolationException e) {
                // Race condition: character was created between our check and save
                logger.warn("Duplicate character creation attempt for userId={} in guildId={}", userId, guildId);
                event.reply("❌ You already have a character in this server! Another request was processed first.")
                    .setEphemeral(true)
                    .queue();
            }

        } catch (Exception e) {
            logger.error("Error handling create-character command for user={}", event.getUser().getId(), e);
            event.reply("❌ An error occurred while creating your character.")
                .setEphemeral(true)
                .queue();
        }
    }

    private void sendErrorMessage(SlashCommandInteractionEvent event, PlayerCharacter character) {
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        int totalUsed = validationService.calculatePointBuyTotal(character);
        int totalAllowed = pointBuy.getTotalPoints();
        int minScore = pointBuy.getMinScore();
        int maxScore = pointBuy.getMaxScore();

        StringBuilder error = new StringBuilder("❌ **Invalid Character**\n\n");
        
        // Check specific validation failures
        error.append("**Issues Found:**\n");
        
        // Validate class/race
        if (!validationService.isValidClass(character.getCharacterClass())) {
            error.append(String.format("• Invalid class. Choose: %s\n", 
                String.join(", ", CharacterConstants.VALID_CLASSES)));
        }
        if (!validationService.isValidRace(character.getRace())) {
            error.append(String.format("• Invalid race. Choose: %s\n", 
                String.join(", ", CharacterConstants.VALID_RACES)));
        }
        
        // Validate score ranges
        int[] scores = {
            character.getStrength(), character.getDexterity(), character.getConstitution(),
            character.getIntelligence(), character.getWisdom(), character.getCharisma()
        };
        String[] names = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};
        
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] < minScore || scores[i] > maxScore) {
                error.append(String.format("• %s score %d is out of range (%d-%d)\n", 
                    names[i], scores[i], minScore, maxScore));
            }
        }
        
        // Validate point-buy budget
        if (totalUsed != totalAllowed) {
            error.append(String.format("• Point-buy total: %d (expected %d)\n", 
                totalUsed, totalAllowed));
        }

        event.reply(error.toString()).setEphemeral(true).queue();
    }

    private void sendSuccessEmbed(SlashCommandInteractionEvent event, PlayerCharacter character) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("✅ Character Created!");
        embed.setColor(Color.GREEN);

        String description = String.format(
            "**Class:** %s\n**Race:** %s\n\nYour character has been created successfully!",
            character.getCharacterClass(),
            character.getRace()
        );
        embed.setDescription(description);

        // Add ability scores
        embed.addField("💪 STR", String.valueOf(character.getStrength()), true);
        embed.addField("🤸 DEX", String.valueOf(character.getDexterity()), true);
        embed.addField("❤️ CON", String.valueOf(character.getConstitution()), true);
        embed.addField("🧠 INT", String.valueOf(character.getIntelligence()), true);
        embed.addField("🦉 WIS", String.valueOf(character.getWisdom()), true);
        embed.addField("✨ CHA", String.valueOf(character.getCharisma()), true);

        // Show point-buy usage
        int totalUsed = validationService.calculatePointBuyTotal(character);
        embed.setFooter(String.format("Point-Buy: %d/%d points used", 
            totalUsed, battleProperties.getCharacter().getPointBuy().getTotalPoints()));

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
