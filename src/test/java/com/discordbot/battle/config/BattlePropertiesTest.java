package com.discordbot.battle.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BattleProperties configuration binding and defaults.
 */
@SpringBootTest(classes = BattleProperties.class)
@EnableConfigurationProperties(BattleProperties.class)
@TestPropertySource(properties = {
    "battle.enabled=true",
    "battle.debug=true",
    "battle.character.pointBuy.totalPoints=30",
    "battle.character.pointBuy.minScore=6",
    "battle.character.pointBuy.maxScore=16",
    "battle.classConfig.warrior.baseHp=15",
    "battle.combat.turn.timeoutSeconds=60",
    "battle.combat.crit.multiplier=2.5",
    "battle.challenge.expireSeconds=180",
    "battle.progression.elo.k=40"
})
class BattlePropertiesTest {

    @Autowired
    private BattleProperties battleProperties;

    @Test
    void testEnabledFlagBinding() {
        assertTrue(battleProperties.isEnabled(), "battle.enabled should be true");
    }

    @Test
    void testDebugFlagBinding() {
        assertTrue(battleProperties.isDebug(), "battle.debug should be true");
    }

    @Test
    void testCharacterPointBuyConfiguration() {
        var pointBuy = battleProperties.getCharacter().getPointBuy();
        
        assertEquals(30, pointBuy.getTotalPoints(), 
            "Point buy total should be 30");
        assertEquals(6, pointBuy.getMinScore(), 
            "Min score should be 6");
        assertEquals(16, pointBuy.getMaxScore(), 
            "Max score should be 16");
    }

    @Test
    void testClassConfigBinding() {
        var warrior = battleProperties.getClassConfig().getWarrior();
        
        assertNotNull(warrior, "Warrior config should not be null");
        assertEquals(15, warrior.getBaseHp(), 
            "Warrior base HP should be 15 (custom value)");
    }

    @Test
    void testCombatConfiguration() {
        var combat = battleProperties.getCombat();
        
        assertEquals(60, combat.getTurn().getTimeoutSeconds(), 
            "Turn timeout should be 60 seconds");
        assertEquals(2.5, combat.getCrit().getMultiplier(), 0.01, 
            "Critical hit multiplier should be 2.5");
    }

    @Test
    void testChallengeConfiguration() {
        var challenge = battleProperties.getChallenge();
        
        assertEquals(180, challenge.getExpireSeconds(), 
            "Challenge expire timeout should be 180 seconds");
    }

    @Test
    void testProgressionConfiguration() {
        var progression = battleProperties.getProgression();
        
        assertEquals(40, progression.getElo().getK(), 
            "ELO K-factor should be 40 (custom value)");
    }
}
