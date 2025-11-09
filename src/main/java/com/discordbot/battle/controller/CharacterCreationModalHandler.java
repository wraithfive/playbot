package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.CharacterConstants;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.CharacterValidationService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.Optional;

/**
 * Handles modal submissions for character creation.
 * Only active in non-test profiles (excludes repository-test profile).
 */
@Component
@Profile("!repository-test")
public class CharacterCreationModalHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CharacterCreationModalHandler.class);

    private final BattleProperties battleProperties;
    private final CharacterValidationService validationService;
    private final PlayerCharacterRepository repository;

    public CharacterCreationModalHandler(BattleProperties battleProperties,
                                        CharacterValidationService validationService,
                                        PlayerCharacterRepository repository) {
        this.battleProperties = battleProperties;
        this.validationService = validationService;
        this.repository = repository;
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String modalId = event.getModalId();
        
        // Check if this is a character creation modal
        if (!modalId.startsWith("char-create:")) {
            return;
        }
        
        // Extract and validate user ID from modal
        String expectedUserId = modalId.substring("char-create:".length());
        if (!expectedUserId.equals(event.getUser().getId())) {
            event.reply("❌ This character creation form is not for you!")
                .setEphemeral(true)
                .queue();
            return;
        }
        
        handleCharacterCreation(event);
    }

    private void handleCharacterCreation(ModalInteractionEvent event) {
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
            
            // Extract values from modal
            String characterClass = event.getValue("class").getAsString().trim();
            String race = event.getValue("race").getAsString().trim();
            String statsString = event.getValue("stats").getAsString().trim();
            
            // Parse stats
            int[] stats = parseStats(statsString);
            if (stats == null) {
                event.reply("❌ Invalid stat format! Please use 6 numbers separated by spaces (e.g., `15 14 13 12 10 8`)")
                    .setEphemeral(true)
                    .queue();
                return;
            }
            
            // Create character entity
            PlayerCharacter character = new PlayerCharacter(
                userId, guildId,
                characterClass, race,
                stats[0], stats[1], stats[2],
                stats[3], stats[4], stats[5]
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
            logger.error("Error handling character creation modal for user={}", event.getUser().getId(), e);
            event.reply("❌ An error occurred while creating your character.")
                .setEphemeral(true)
                .queue();
        }
    }

    /**
     * Parses a space-separated string of 6 integers.
     * Returns null if parsing fails or if there aren't exactly 6 values.
     */
    private int[] parseStats(String statsString) {
        String[] parts = statsString.split("\\s+");
        if (parts.length != 6) {
            return null;
        }
        
        int[] stats = new int[6];
        try {
            for (int i = 0; i < 6; i++) {
                stats[i] = Integer.parseInt(parts[i]);
            }
            return stats;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sendErrorMessage(ModalInteractionEvent event, PlayerCharacter character) {
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        int totalUsed = validationService.calculatePointBuyTotal(character);
        int totalAllowed = pointBuy.getTotalPoints();
        int minScore = pointBuy.getMinScore();
        int maxScore = pointBuy.getMaxScore();

        StringBuilder errorMsg = new StringBuilder("❌ **Invalid Character**\n\n");
        errorMsg.append("**Issues Found:**\n");
        
        // Check class/race validity
        if (!validationService.isValidClass(character.getCharacterClass())) {
            errorMsg.append(String.format("• Invalid class '%s'. Choose: %s\n", 
                character.getCharacterClass(),
                String.join(", ", CharacterConstants.VALID_CLASSES)));
        }
        if (!validationService.isValidRace(character.getRace())) {
            errorMsg.append(String.format("• Invalid race '%s'. Choose: %s\n", 
                character.getRace(),
                String.join(", ", CharacterConstants.VALID_RACES)));
        }
        
        // Check stat ranges
        int[] scores = {
            character.getStrength(), character.getDexterity(), character.getConstitution(),
            character.getIntelligence(), character.getWisdom(), character.getCharisma()
        };
        String[] names = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};
        
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] < minScore || scores[i] > maxScore) {
                errorMsg.append(String.format("• %s score %d is out of range (%d-%d)\n", 
                    names[i], scores[i], minScore, maxScore));
            }
        }
        
        // Check point-buy budget
        if (totalUsed != totalAllowed) {
            errorMsg.append(String.format("• Point-buy total: %d (expected %d)\n", 
                totalUsed, totalAllowed));
        }
        
        errorMsg.append("\nUse `/battle-help` to see the point-buy cost table.");

        event.reply(errorMsg.toString())
            .setEphemeral(true)
            .queue();
    }

    private void sendSuccessEmbed(ModalInteractionEvent event, PlayerCharacter character) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚔️ Character Created!");
        embed.setColor(Color.GREEN);
        embed.setDescription(String.format("Welcome, brave %s!", character.getCharacterClass()));

        embed.addField("Class", character.getCharacterClass(), true);
        embed.addField("Race", character.getRace(), true);
        embed.addField("\u200B", "\u200B", true); // Spacer

        // Ability scores with modifiers
        embed.addField("💪 STR", formatStat(character.getStrength()), true);
        embed.addField("🏃 DEX", formatStat(character.getDexterity()), true);
        embed.addField("❤️ CON", formatStat(character.getConstitution()), true);
        embed.addField("🧠 INT", formatStat(character.getIntelligence()), true);
        embed.addField("🦉 WIS", formatStat(character.getWisdom()), true);
        embed.addField("✨ CHA", formatStat(character.getCharisma()), true);

        embed.addField("Next Steps",
            "Your character is ready for battle!",
            false);

        embed.setFooter("Battle System | Character ID: " + character.getId());
        embed.setTimestamp(character.getCreatedAt());

        event.replyEmbeds(embed.build())
            .setEphemeral(true)
            .queue();
    }

    private String formatStat(int score) {
        int modifier = (score - 10) / 2;
        String modStr = modifier >= 0 ? "+" + modifier : String.valueOf(modifier);
        return String.format("%d (%s)", score, modStr);
    }
}
