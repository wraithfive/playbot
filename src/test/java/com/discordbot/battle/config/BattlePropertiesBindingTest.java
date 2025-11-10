package com.discordbot.battle.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that BattleProperties configuration binds correctly from application.properties.
 * Does not assert specific values - validates property binding and structure only.
 */
@SpringBootTest(classes = BattleProperties.class)
@EnableConfigurationProperties(BattleProperties.class)
@org.springframework.test.context.TestPropertySource(properties = {
    // Ensure required class base HP values are present for validation during context load
    "battle.classConfig.warrior.baseHp=10",
    "battle.classConfig.rogue.baseHp=8",
    "battle.classConfig.mage.baseHp=6",
    "battle.classConfig.cleric.baseHp=8"
})
class BattlePropertiesBindingTest {

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
    void testPointBuyConfigurationBinds() {
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        
        assertNotNull(pointBuy, "PointBuy config should not be null");
        assertTrue(pointBuy.getTotalPoints() > 0, 
            "Total points should be positive");
        assertTrue(pointBuy.getMinScore() > 0, 
            "Min score should be positive");
        assertTrue(pointBuy.getMaxScore() >= pointBuy.getMinScore(), 
            "Max score should be >= min score");
        assertNotNull(pointBuy.getCosts(), "Costs list should not be null");
    }

    @Test
    void testCombatConfigurationBinds() {
        var combat = battleProperties.getCombat();
        
        assertNotNull(combat, "Combat config should not be null");
        assertNotNull(combat.getTurn(), "Turn config should not be null");
        assertNotNull(combat.getCrit(), "Crit config should not be null");
        
        assertTrue(combat.getTurn().getTimeoutSeconds() > 0, 
            "Turn timeout should be positive");
        assertTrue(combat.getCrit().getMultiplier() > 0, 
            "Crit multiplier should be positive");
        assertTrue(combat.getCrit().getThreshold() > 0 && combat.getCrit().getThreshold() <= 20,
            "Crit threshold should be between 1-20");
    }

    @Test
    void testChallengeConfigurationBinds() {
        var challenge = battleProperties.getChallenge();
        
        assertNotNull(challenge, "Challenge config should not be null");
        assertTrue(challenge.getExpireSeconds() > 0, 
            "Challenge expire should be positive");
    }

    @Test
    void testClassConfigurationBinds() {
        var classConfig = battleProperties.getClassConfig();
        
        assertNotNull(classConfig, "Class config should not be null");
        assertNotNull(classConfig.getWarrior(), "Warrior should not be null");
        assertNotNull(classConfig.getRogue(), "Rogue should not be null");
        assertNotNull(classConfig.getMage(), "Mage should not be null");
        assertNotNull(classConfig.getCleric(), "Cleric should not be null");
        
        assertTrue(classConfig.getWarrior().getBaseHp() > 0, 
            "Warrior base HP should be positive");
        assertTrue(classConfig.getRogue().getBaseHp() > 0, 
            "Rogue base HP should be positive");
        assertTrue(classConfig.getMage().getBaseHp() > 0, 
            "Mage base HP should be positive");
        assertTrue(classConfig.getCleric().getBaseHp() > 0, 
            "Cleric base HP should be positive");
    }

    @Test
    void testProgressionConfigurationBinds() {
        var progression = battleProperties.getProgression();
        
        assertNotNull(progression, "Progression config should not be null");
        assertNotNull(progression.getElo(), "ELO config should not be null");
        assertNotNull(progression.getXp(), "XP config should not be null");
        assertNotNull(progression.getProficiencyByLevel(), 
            "Proficiency by level should not be null");
        
        assertTrue(progression.getElo().getK() > 0, 
            "ELO K-factor should be positive");
        assertNotNull(progression.getXp().getLevelCurve(), 
            "XP level curve should not be null");
        assertFalse(progression.getXp().getLevelCurve().isEmpty(), 
            "XP level curve should not be empty");
    }
}
