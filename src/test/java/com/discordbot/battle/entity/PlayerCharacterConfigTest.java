package com.discordbot.battle.entity;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.service.CharacterValidationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that character validation adapts to different configuration values.
 * Proves the validation service works with any valid point-buy configuration.
 * 
 * NOTE: These tests use programmatically-set configs, not application.properties.
 * This allows testing different point-buy systems without modifying the main config.
 */
class PlayerCharacterConfigTest {

    @Test
    void customConfig_30Points_validatesCorrectly() {
        // Custom config: 30 points, 6-16 range
        BattleProperties properties = new BattleProperties();
        // Set required class configs
        properties.getClassConfig().getWarrior().setBaseHp(10);
        properties.getClassConfig().getRogue().setBaseHp(8);
        properties.getClassConfig().getMage().setBaseHp(6);
        properties.getClassConfig().getCleric().setBaseHp(8);
        
        var pointBuy = properties.getCharacter().getPointBuy();
        pointBuy.setTotalPoints(30);
        pointBuy.setMinScore(6);
        pointBuy.setMaxScore(16);
        
        // Point-buy costs indexed by (score - minScore):
        // Score: 6  7  8  9  10 11 12 13 14  15  16
        // Cost:  0  1  2  3  4  5  7  9  12  15  19
        // Example: score of 16 costs 19 points (index 16-6=10, costs[10]=19)
        //          score of 12 costs 7 points (index 12-6=6, costs[6]=7)
        pointBuy.setCosts(List.of(0, 1, 2, 3, 4, 5, 7, 9, 12, 15, 19));
        
        CharacterValidationService validator = new CharacterValidationService(properties);
        
        // Character with 30-point allocation:
        // STR=16 costs 19 pts, DEX=12 costs 7 pts, CON=10 costs 4 pts, rest at min (6) costs 0 pts each
        // Total: 19 + 7 + 4 + 0 + 0 + 0 = 30 points
        PlayerCharacter pc = new PlayerCharacter(
            "user123", "guild456",
            "Warrior",
            "Human",
            16, 12, 10, 6, 6, 6  // STR, DEX, CON, INT, WIS, CHA
        );
        
        assertTrue(validator.isValid(pc), "Should validate with custom 30-point budget");
        assertEquals(30, validator.calculatePointBuyTotal(pc));
    }

    @Test
    void customConfig_20Points_validatesCorrectly() {
        // Lower-powered config: 20 points, 7-14 range
        BattleProperties properties = new BattleProperties();
        // Set required class configs
        properties.getClassConfig().getWarrior().setBaseHp(10);
        properties.getClassConfig().getRogue().setBaseHp(8);
        properties.getClassConfig().getMage().setBaseHp(6);
        properties.getClassConfig().getCleric().setBaseHp(8);
        
        var pointBuy = properties.getCharacter().getPointBuy();
        pointBuy.setTotalPoints(20);
        pointBuy.setMinScore(7);
        pointBuy.setMaxScore(14);
        
        // Point-buy costs indexed by (score - minScore):
        // Score: 7  8  9  10 11 12 13 14
        // Cost:  0  1  2  3  4  5  7  9
        pointBuy.setCosts(List.of(0, 1, 2, 3, 4, 5, 7, 9));
        
        CharacterValidationService validator = new CharacterValidationService(properties);
        
        // Character with 20-point allocation:
        // STR=14 costs 9 pts, DEX=13 costs 7 pts, CON=11 costs 4 pts, rest at min (7) costs 0 pts each
        // Total: 9 + 7 + 4 + 0 + 0 + 0 = 20 points
        PlayerCharacter pc = new PlayerCharacter(
            "user123", "guild456",
            "Mage",
            "Elf",
            14, 13, 11, 7, 7, 7  // STR, DEX, CON, INT, WIS, CHA
        );
        
        assertTrue(validator.isValid(pc), "Should validate with custom 20-point budget");
        assertEquals(20, validator.calculatePointBuyTotal(pc));
    }

    @Test
    void customConfig_exceedsBudget_fails() {
        // 20-point budget config
        BattleProperties properties = new BattleProperties();
        // Set required class configs
        properties.getClassConfig().getWarrior().setBaseHp(10);
        properties.getClassConfig().getRogue().setBaseHp(8);
        properties.getClassConfig().getMage().setBaseHp(6);
        properties.getClassConfig().getCleric().setBaseHp(8);
        
        var pointBuy = properties.getCharacter().getPointBuy();
        pointBuy.setTotalPoints(20);
        pointBuy.setMinScore(7);
        pointBuy.setMaxScore(14);
        // Point-buy costs: 7=0, 8=1, 9=2, 10=3, 11=4, 12=5, 13=7, 14=9
        pointBuy.setCosts(List.of(0, 1, 2, 3, 4, 5, 7, 9));
        
        CharacterValidationService validator = new CharacterValidationService(properties);
        
        // Character using 27 points (exceeds 20-point budget):
        // 14=9 pts, 14=9 pts, 14=9 pts, 12=5 pts, 7=0 pts, 7=0 pts
        // Total: 9 + 9 + 9 + 5 + 0 + 0 = 32 points > 20 (should fail)
        PlayerCharacter pc = new PlayerCharacter(
            "user123", "guild456",
            "Cleric",
            "Dwarf",
            14, 14, 14, 12, 7, 7  // Way over 20 points
        );
        
        assertFalse(validator.isValid(pc), "Should fail when exceeding custom budget");
        int actual = validator.calculatePointBuyTotal(pc);
        assertTrue(actual > 20, "Should calculate points over budget");
    }
}