package com.discordbot.battle.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BattleConfigValidator
 * Phase 12: Config & Tuning
 */
class BattleConfigValidatorTest {

    private BattleProperties config;
    private BattleConfigValidator validator;

    @BeforeEach
    void setUp() {
        config = mock(BattleProperties.class);
        validator = new BattleConfigValidator(config);
    }

    @Test
    void testValidateWhenDisabled() {
        when(config.isEnabled()).thenReturn(false);

        // Should not throw exception
        assertDoesNotThrow(() -> validator.validateAndWarn());
    }

    @Test
    void testValidateWithDefaultConfig() {
        // Mock a fully configured valid setup
        when(config.isEnabled()).thenReturn(true);

        // Character config
        BattleProperties.CharacterConfig characterConfig = mock(BattleProperties.CharacterConfig.class);
        BattleProperties.CharacterConfig.PointBuyConfig pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(config.getCharacter()).thenReturn(characterConfig);
        when(characterConfig.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);

        // Combat config
        BattleProperties.CombatConfig combatConfig = mock(BattleProperties.CombatConfig.class);
        BattleProperties.CombatConfig.CritConfig critConfig = mock(BattleProperties.CombatConfig.CritConfig.class);
        BattleProperties.CombatConfig.TurnConfig turnConfig = mock(BattleProperties.CombatConfig.TurnConfig.class);
        when(config.getCombat()).thenReturn(combatConfig);
        when(combatConfig.getCrit()).thenReturn(critConfig);
        when(combatConfig.getTurn()).thenReturn(turnConfig);
        when(critConfig.getThreshold()).thenReturn(20);
        when(critConfig.getMultiplier()).thenReturn(2.0);
        when(turnConfig.getTimeoutSeconds()).thenReturn(45);
        when(combatConfig.getCooldownSeconds()).thenReturn(60);
        when(combatConfig.getMaxConcurrentPerGuild()).thenReturn(50);
        when(combatConfig.getDefendAcBonus()).thenReturn(2);

        // Progression config
        BattleProperties.ProgressionConfig progressionConfig = mock(BattleProperties.ProgressionConfig.class);
        BattleProperties.ProgressionConfig.EloConfig eloConfig = mock(BattleProperties.ProgressionConfig.EloConfig.class);
        BattleProperties.ProgressionConfig.XpConfig xpConfig = mock(BattleProperties.ProgressionConfig.XpConfig.class);
        BattleProperties.ProgressionConfig.ChatXpConfig chatXpConfig = mock(BattleProperties.ProgressionConfig.ChatXpConfig.class);
        when(config.getProgression()).thenReturn(progressionConfig);
        when(progressionConfig.getElo()).thenReturn(eloConfig);
        when(progressionConfig.getXp()).thenReturn(xpConfig);
        when(progressionConfig.getChatXp()).thenReturn(chatXpConfig);
        when(eloConfig.getK()).thenReturn(32);
        when(xpConfig.getBaseXp()).thenReturn(20L);
        when(xpConfig.getWinBonus()).thenReturn(30L);
        when(xpConfig.getDrawBonus()).thenReturn(10L);
        when(chatXpConfig.isEnabled()).thenReturn(true);
        when(chatXpConfig.getBaseXp()).thenReturn(10);
        when(chatXpConfig.getBonusXpMax()).thenReturn(5);
        when(chatXpConfig.getCooldownSeconds()).thenReturn(60);
        when(chatXpConfig.isAutoCreateCharacter()).thenReturn(true);

        // Should not throw exception
        assertDoesNotThrow(() -> validator.validateAndWarn());
    }

    @Test
    void testGenerateHealthReportWhenDisabled() {
        when(config.isEnabled()).thenReturn(false);

        String report = validator.generateHealthReport();

        assertNotNull(report);
        assertTrue(report.contains("DISABLED"));
    }

    @Test
    void testGenerateHealthReportWhenEnabled() {
        // Mock a minimal valid config
        when(config.isEnabled()).thenReturn(true);
        when(config.isDebug()).thenReturn(false);

        // Character config
        BattleProperties.CharacterConfig characterConfig = mock(BattleProperties.CharacterConfig.class);
        BattleProperties.CharacterConfig.PointBuyConfig pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(config.getCharacter()).thenReturn(characterConfig);
        when(characterConfig.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);

        // Combat config
        BattleProperties.CombatConfig combatConfig = mock(BattleProperties.CombatConfig.class);
        BattleProperties.CombatConfig.CritConfig critConfig = mock(BattleProperties.CombatConfig.CritConfig.class);
        BattleProperties.CombatConfig.TurnConfig turnConfig = mock(BattleProperties.CombatConfig.TurnConfig.class);
        when(config.getCombat()).thenReturn(combatConfig);
        when(combatConfig.getCrit()).thenReturn(critConfig);
        when(combatConfig.getTurn()).thenReturn(turnConfig);
        when(critConfig.getThreshold()).thenReturn(20);
        when(critConfig.getMultiplier()).thenReturn(2.0);
        when(turnConfig.getTimeoutSeconds()).thenReturn(45);
        when(combatConfig.getCooldownSeconds()).thenReturn(60);
        when(combatConfig.getMaxConcurrentPerGuild()).thenReturn(50);

        // Progression config
        BattleProperties.ProgressionConfig progressionConfig = mock(BattleProperties.ProgressionConfig.class);
        BattleProperties.ProgressionConfig.EloConfig eloConfig = mock(BattleProperties.ProgressionConfig.EloConfig.class);
        BattleProperties.ProgressionConfig.XpConfig xpConfig = mock(BattleProperties.ProgressionConfig.XpConfig.class);
        BattleProperties.ProgressionConfig.ChatXpConfig chatXpConfig = mock(BattleProperties.ProgressionConfig.ChatXpConfig.class);
        when(config.getProgression()).thenReturn(progressionConfig);
        when(progressionConfig.getElo()).thenReturn(eloConfig);
        when(progressionConfig.getXp()).thenReturn(xpConfig);
        when(progressionConfig.getChatXp()).thenReturn(chatXpConfig);
        when(eloConfig.getK()).thenReturn(32);
        when(xpConfig.getBaseXp()).thenReturn(20L);
        when(xpConfig.getWinBonus()).thenReturn(30L);
        when(xpConfig.getDrawBonus()).thenReturn(10L);
        when(chatXpConfig.isEnabled()).thenReturn(true);
        when(chatXpConfig.getBaseXp()).thenReturn(10);
        when(chatXpConfig.getBonusXpMax()).thenReturn(5);
        when(chatXpConfig.getCooldownSeconds()).thenReturn(60);

        String report = validator.generateHealthReport();

        assertNotNull(report);
        assertTrue(report.contains("ENABLED"));
        assertTrue(report.contains("Point-Buy"));
        assertTrue(report.contains("Combat"));
        assertTrue(report.contains("Progression"));
    }

    @Test
    void testHealthReportWithDebugMode() {
        when(config.isEnabled()).thenReturn(true);
        when(config.isDebug()).thenReturn(true);

        // Minimal mocks
        BattleProperties.CharacterConfig characterConfig = mock(BattleProperties.CharacterConfig.class);
        BattleProperties.CharacterConfig.PointBuyConfig pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(config.getCharacter()).thenReturn(characterConfig);
        when(characterConfig.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);

        BattleProperties.CombatConfig combatConfig = mock(BattleProperties.CombatConfig.class);
        BattleProperties.CombatConfig.CritConfig critConfig = mock(BattleProperties.CombatConfig.CritConfig.class);
        BattleProperties.CombatConfig.TurnConfig turnConfig = mock(BattleProperties.CombatConfig.TurnConfig.class);
        when(config.getCombat()).thenReturn(combatConfig);
        when(combatConfig.getCrit()).thenReturn(critConfig);
        when(combatConfig.getTurn()).thenReturn(turnConfig);
        when(critConfig.getThreshold()).thenReturn(20);
        when(critConfig.getMultiplier()).thenReturn(2.0);
        when(turnConfig.getTimeoutSeconds()).thenReturn(45);
        when(combatConfig.getCooldownSeconds()).thenReturn(60);
        when(combatConfig.getMaxConcurrentPerGuild()).thenReturn(50);

        BattleProperties.ProgressionConfig progressionConfig = mock(BattleProperties.ProgressionConfig.class);
        BattleProperties.ProgressionConfig.EloConfig eloConfig = mock(BattleProperties.ProgressionConfig.EloConfig.class);
        BattleProperties.ProgressionConfig.XpConfig xpConfig = mock(BattleProperties.ProgressionConfig.XpConfig.class);
        BattleProperties.ProgressionConfig.ChatXpConfig chatXpConfig = mock(BattleProperties.ProgressionConfig.ChatXpConfig.class);
        when(config.getProgression()).thenReturn(progressionConfig);
        when(progressionConfig.getElo()).thenReturn(eloConfig);
        when(progressionConfig.getXp()).thenReturn(xpConfig);
        when(progressionConfig.getChatXp()).thenReturn(chatXpConfig);
        when(eloConfig.getK()).thenReturn(32);
        when(xpConfig.getBaseXp()).thenReturn(20L);
        when(xpConfig.getWinBonus()).thenReturn(30L);
        when(xpConfig.getDrawBonus()).thenReturn(10L);
        when(chatXpConfig.isEnabled()).thenReturn(true);
        when(chatXpConfig.getBaseXp()).thenReturn(10);
        when(chatXpConfig.getBonusXpMax()).thenReturn(5);
        when(chatXpConfig.getCooldownSeconds()).thenReturn(60);

        String report = validator.generateHealthReport();

        assertTrue(report.contains("debug mode"));
    }

    @Test
    void testHealthReportIncludesAllSections() {
        when(config.isEnabled()).thenReturn(true);
        when(config.isDebug()).thenReturn(false);

        // Full mock setup
        BattleProperties.CharacterConfig characterConfig = mock(BattleProperties.CharacterConfig.class);
        BattleProperties.CharacterConfig.PointBuyConfig pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(config.getCharacter()).thenReturn(characterConfig);
        when(characterConfig.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);

        BattleProperties.CombatConfig combatConfig = mock(BattleProperties.CombatConfig.class);
        BattleProperties.CombatConfig.CritConfig critConfig = mock(BattleProperties.CombatConfig.CritConfig.class);
        BattleProperties.CombatConfig.TurnConfig turnConfig = mock(BattleProperties.CombatConfig.TurnConfig.class);
        when(config.getCombat()).thenReturn(combatConfig);
        when(combatConfig.getCrit()).thenReturn(critConfig);
        when(combatConfig.getTurn()).thenReturn(turnConfig);
        when(critConfig.getThreshold()).thenReturn(20);
        when(critConfig.getMultiplier()).thenReturn(2.0);
        when(turnConfig.getTimeoutSeconds()).thenReturn(45);
        when(combatConfig.getCooldownSeconds()).thenReturn(60);
        when(combatConfig.getMaxConcurrentPerGuild()).thenReturn(50);

        BattleProperties.ProgressionConfig progressionConfig = mock(BattleProperties.ProgressionConfig.class);
        BattleProperties.ProgressionConfig.EloConfig eloConfig = mock(BattleProperties.ProgressionConfig.EloConfig.class);
        BattleProperties.ProgressionConfig.XpConfig xpConfig = mock(BattleProperties.ProgressionConfig.XpConfig.class);
        BattleProperties.ProgressionConfig.ChatXpConfig chatXpConfig = mock(BattleProperties.ProgressionConfig.ChatXpConfig.class);
        when(config.getProgression()).thenReturn(progressionConfig);
        when(progressionConfig.getElo()).thenReturn(eloConfig);
        when(progressionConfig.getXp()).thenReturn(xpConfig);
        when(progressionConfig.getChatXp()).thenReturn(chatXpConfig);
        when(eloConfig.getK()).thenReturn(32);
        when(xpConfig.getBaseXp()).thenReturn(20L);
        when(xpConfig.getWinBonus()).thenReturn(30L);
        when(xpConfig.getDrawBonus()).thenReturn(10L);
        when(chatXpConfig.isEnabled()).thenReturn(true);
        when(chatXpConfig.getBaseXp()).thenReturn(10);
        when(chatXpConfig.getBonusXpMax()).thenReturn(5);
        when(chatXpConfig.getCooldownSeconds()).thenReturn(60);

        String report = validator.generateHealthReport();

        // Verify all major sections are present
        assertTrue(report.contains("Battle System Configuration Health Report"));
        assertTrue(report.contains("Point-Buy:"));
        assertTrue(report.contains("Combat:"));
        assertTrue(report.contains("Crit:"));
        assertTrue(report.contains("Turn timeout:"));
        assertTrue(report.contains("Battle cooldown:"));
        assertTrue(report.contains("Max concurrent/guild:"));
        assertTrue(report.contains("Progression:"));
        assertTrue(report.contains("ELO K-factor:"));
        assertTrue(report.contains("Battle XP:"));
        assertTrue(report.contains("Chat XP:"));
    }
}
