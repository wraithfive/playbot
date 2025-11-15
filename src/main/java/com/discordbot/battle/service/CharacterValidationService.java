package com.discordbot.battle.service;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.CharacterConstants;
import com.discordbot.battle.entity.PlayerCharacter;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for validating PlayerCharacter entities using configurable rules.
 */
@Service
public class CharacterValidationService {

    private final BattleProperties battleProperties;

    public CharacterValidationService(BattleProperties battleProperties) {
        this.battleProperties = battleProperties;
    }

    /**
     * Validates character according to configured point-buy rules.
     *
     * @param character the character to validate
     * @return true if character is valid according to all rules, false otherwise
     */
    public boolean isValid(PlayerCharacter character) {
        // Null check
        if (character == null) {
            return false;
        }

        // Validate class and race
        if (!isValidClass(character.getCharacterClass()) || !isValidRace(character.getRace())) {
            return false;
        }

        var pointBuy = battleProperties.getCharacter().getPointBuy();
        int minScore = pointBuy.getMinScore();
        int maxScore = pointBuy.getMaxScore();

        // Validate all scores are in valid range
        int[] scores = {
            character.getStrength(), 
            character.getDexterity(), 
            character.getConstitution(),
            character.getIntelligence(), 
            character.getWisdom(), 
            character.getCharisma()
        };
        
        for (int score : scores) {
            if (score < minScore || score > maxScore) {
                return false;
            }
        }

        // Validate point-buy total matches budget
        int total = calculatePointBuyTotal(character);
        return total == pointBuy.getTotalPoints();
    }

    /**
     * Validates if the given character class is valid.
     * 
     * @param characterClass the character class to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidClass(String characterClass) {
        return characterClass != null && CharacterConstants.VALID_CLASSES.contains(characterClass);
    }

    /**
     * Validates if the given race is valid.
     * 
     * @param race the race to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidRace(String race) {
        return race != null && CharacterConstants.VALID_RACES.contains(race);
    }

    /**
     * Calculate total point-buy cost of ability scores.
     *
     * @param character the character whose point-buy cost to calculate
     * @return total point-buy cost, or 0 if character is null
     */
    public int calculatePointBuyTotal(PlayerCharacter character) {
        if (character == null) {
            return 0;
        }

        var pointBuy = battleProperties.getCharacter().getPointBuy();
        List<Integer> costs = pointBuy.getCosts();
        int minScore = pointBuy.getMinScore();

        int[] scores = {
            character.getStrength(), 
            character.getDexterity(), 
            character.getConstitution(),
            character.getIntelligence(), 
            character.getWisdom(), 
            character.getCharisma()
        };

        int total = 0;
        for (int score : scores) {
            int index = score - minScore;
            if (index >= 0 && index < costs.size()) {
                total += costs.get(index);
            }
        }
        return total;
    }
}
