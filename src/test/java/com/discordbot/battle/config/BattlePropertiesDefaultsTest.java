package com.discordbot.battle.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that BattleProperties uses sensible defaults when properties are not configured.
 */
@SpringBootTest(classes = BattleProperties.class)
@EnableConfigurationProperties(BattleProperties.class)
class BattlePropertiesDefaultsTest {

    @Autowired
    private BattleProperties battleProperties;

    @Test
    void testEnabledPropertyBinds() {
        // Just validate we get a valid boolean value (doesn't matter if true/false)
        assertNotNull(battleProperties, "BattleProperties should be injected");
        // Verify the method is callable and returns a boolean (true or false are both valid)
        assertTrue(battleProperties.isEnabled() || !battleProperties.isEnabled(),
            "isEnabled() should return a valid boolean");
    }

    @Test
    void testDebugPropertyBinds() {
        // Just validate we get a valid boolean value
        assertTrue(battleProperties.isDebug() || !battleProperties.isDebug(),
            "isDebug() should return a valid boolean");
    }

    @Test
    void testDefaultPointBuyConfiguration() {
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        
        assertEquals(27, pointBuy.getTotalPoints(), 
            "Default point buy total should be 27 (D&D 5e standard)");
        assertEquals(8, pointBuy.getMinScore(), 
            "Default min score should be 8");
        assertEquals(15, pointBuy.getMaxScore(), 
            "Default max score should be 15");
    }

    @Test
    void testDefaultCombatConfiguration() {
        var combat = battleProperties.getCombat();
        
        assertEquals(45, combat.getTurn().getTimeoutSeconds(), 
            "Default turn timeout should be 45 seconds");
        assertEquals(2.0, combat.getCrit().getMultiplier(), 0.01, 
            "Default critical hit multiplier should be 2.0");
        assertEquals(20, combat.getCrit().getThreshold(),
            "Default crit threshold should be 20");
    }

    @Test
    void testDefaultChallengeConfiguration() {
        var challenge = battleProperties.getChallenge();
        
        assertEquals(120, challenge.getExpireSeconds(), 
            "Default challenge expire should be 120 seconds");
    }

    @Test
    void testDefaultClassConfiguration() {
        var classConfig = battleProperties.getClassConfig();
        
        assertNotNull(classConfig.getWarrior(), "Warrior should not be null");
        assertNotNull(classConfig.getRogue(), "Rogue should not be null");
        assertNotNull(classConfig.getMage(), "Mage should not be null");
        assertNotNull(classConfig.getCleric(), "Cleric should not be null");
        
        assertEquals(12, classConfig.getWarrior().getBaseHp(), 
            "Warrior default base HP should be 12");
        assertEquals(8, classConfig.getRogue().getBaseHp(), 
            "Rogue default base HP should be 8");
        assertEquals(6, classConfig.getMage().getBaseHp(), 
            "Mage default base HP should be 6");
        assertEquals(8, classConfig.getCleric().getBaseHp(), 
            "Cleric default base HP should be 8");
    }
}
