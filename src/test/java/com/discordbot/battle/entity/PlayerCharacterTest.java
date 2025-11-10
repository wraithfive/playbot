package com.discordbot.battle.entity;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.service.CharacterValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: PlayerCharacter entity tests for Phase 1 character creation.
 * Tests dynamically adapt to BattleProperties defaults (mimics application.properties).
 * 
 * NOTE: Uses Java default config values (27 points, 8-15 range) which match application.properties.
 * If application.properties point-buy config changes, update the defaults in BattleProperties.java
 * to keep these tests aligned.
 */
class PlayerCharacterTest {

    private CharacterValidationService validator;
    private BattleProperties properties;
    private int totalPoints;
    private int minScore;
    private int maxScore;

    @BeforeEach
    void setUp() {
        // Load config from application.properties
        properties = new BattleProperties();
        // Set required class configs (D&D 5e hit die maximums)
        properties.getClassConfig().getWarrior().setBaseHp(10);
        properties.getClassConfig().getRogue().setBaseHp(8);
        properties.getClassConfig().getMage().setBaseHp(6);
        properties.getClassConfig().getCleric().setBaseHp(8);
        
        validator = new CharacterValidationService(properties);
        
        // Extract config values for dynamic testing
        var pointBuy = properties.getCharacter().getPointBuy();
        totalPoints = pointBuy.getTotalPoints();
        minScore = pointBuy.getMinScore();
        maxScore = pointBuy.getMaxScore();
    }

    @Test
    void validCharacter_passesValidation() {
        // Build a valid character using max budget efficiently
        // Strategy: max one stat, high second stat, distribute rest to hit exact budget
        PlayerCharacter pc = new PlayerCharacter(
            "user123", "guild456",
            "Warrior",
            "Human",
            maxScore,      // Primary stat at max
            maxScore - 1,  // Secondary stat high
            maxScore - 2,  // Tertiary stat
            maxScore - 3,  // Quaternary
            minScore + 2,  // Low stat
            minScore       // Dump stat
        );
        assertTrue(validator.isValid(pc), 
            String.format("Character should pass validation with budget=%d, range=[%d-%d]", 
                totalPoints, minScore, maxScore));
        assertEquals(totalPoints, validator.calculatePointBuyTotal(pc), 
            "Should use exactly the configured point budget");
    }

    @Test
    void invalidScore_failsValidation() {
        // Score above configured max
        PlayerCharacter pc = new PlayerCharacter(
            "user123", "guild456",
            "Mage",
            "Elf",
            maxScore + 1,  // Intentionally above max
            maxScore,
            minScore,
            minScore,
            minScore,
            minScore
        );
        assertFalse(validator.isValid(pc), 
            String.format("Score above max (%d) should fail validation", maxScore));
    }

    @Test
    void scoreBelowMin_failsValidation() {
        // Score below configured min
        PlayerCharacter pc = new PlayerCharacter(
            "user123", "guild456",
            "Rogue",
            "Halfling",
            minScore - 1,  // Intentionally below min
            minScore,
            minScore,
            minScore,
            minScore,
            minScore
        );
        assertFalse(validator.isValid(pc), 
            String.format("Score below min (%d) should fail validation", minScore));
    }

    @Test
    void underBudget_failsValidation() {
        // All minimum scores = 0 points (under budget)
        PlayerCharacter pc = new PlayerCharacter(
            "user123", "guild456",
            "Rogue",
            "Halfling",
            minScore, minScore, minScore, minScore, minScore, minScore
        );
        int actualPoints = validator.calculatePointBuyTotal(pc);
        assertFalse(validator.isValid(pc), 
            String.format("Under budget (%d < %d) should fail validation", actualPoints, totalPoints));
        assertEquals(0, actualPoints, "All min scores should cost 0 points");
    }

    @Test
    void overBudget_failsValidation() {
        // All max scores = way over budget
        PlayerCharacter pc = new PlayerCharacter(
            "user123", "guild456",
            "Cleric",
            "Dwarf",
            maxScore, maxScore, maxScore, maxScore, maxScore, maxScore
        );
        int actualPoints = validator.calculatePointBuyTotal(pc);
        assertFalse(validator.isValid(pc), 
            String.format("Over budget (%d > %d) should fail validation", actualPoints, totalPoints));
        assertTrue(actualPoints > totalPoints, "All max scores should exceed budget");
    }

    @Test
    void invalidClassOrRace_failsValidation() {
        // Valid point-buy but invalid class and race
        PlayerCharacter pc = new PlayerCharacter(
            "user123", "guild456",
            "Ninja", // Invalid class
            "Orc",   // Invalid race
            maxScore,
            maxScore - 1,
            maxScore - 2,
            maxScore - 3,
            minScore + 2,
            minScore
        );
        assertFalse(validator.isValid(pc), "Invalid class or race should fail validation");
        assertEquals(totalPoints, validator.calculatePointBuyTotal(pc), 
            "Points should be valid but class/race invalid");
    }
}
