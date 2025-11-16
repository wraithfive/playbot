package com.discordbot.battle.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BattleMetricsService.
 * Tests Phase 8 metrics collection using Micrometer.
 */
class BattleMetricsServiceTest {

    private SimpleMeterRegistry registry;
    private BattleMetricsService metricsService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsService = new BattleMetricsService(registry);
    }

    @Test
    void recordChallengeCreated_incrementsCounterAndPending() {
        // When: Record challenge created
        metricsService.recordChallengeCreated();

        // Then: Counter and pending gauge increase
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.challengesCreated());
        assertEquals(1, stats.pendingChallenges());
    }

    @Test
    void recordChallengeAccepted_incrementsCounterAndUpdatesGauges() {
        // Given: One pending challenge
        metricsService.recordChallengeCreated();

        // When: Accept challenge
        metricsService.recordChallengeAccepted();

        // Then: Accepted increments, pending decrements, active increments
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.challengesAccepted());
        assertEquals(0, stats.pendingChallenges());
        assertEquals(1, stats.activeBattles());
    }

    @Test
    void recordChallengeDeclined_incrementsCounterAndDecrementsPending() {
        // Given: One pending challenge
        metricsService.recordChallengeCreated();

        // When: Decline challenge
        metricsService.recordChallengeDeclined();

        // Then: Declined increments, pending decrements
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.challengesDeclined());
        assertEquals(0, stats.pendingChallenges());
    }

    @Test
    void recordChallengeExpired_decrementsPendingOnly() {
        // Given: One pending challenge
        metricsService.recordChallengeCreated();

        // When: Challenge expires
        metricsService.recordChallengeExpired();

        // Then: Pending decrements (no counters increment)
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(0, stats.pendingChallenges());
        assertEquals(0, stats.challengesAccepted());
        assertEquals(0, stats.challengesDeclined());
    }

    @Test
    void recordBattleCompleted_incrementsCounterAndDecrementsActive() {
        // Given: One active battle
        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();

        // When: Battle completes
        metricsService.recordBattleCompleted(30000);

        // Then: Completed increments, active decrements
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.battlesCompleted());
        assertEquals(0, stats.activeBattles());
        assertEquals(30000.0, stats.avgBattleDurationMs(), 0.01);
    }

    @Test
    void recordBattleForfeit_incrementsCounterAndDecrementsActive() {
        // Given: One active battle
        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();

        // When: Battle forfeited
        metricsService.recordBattleForfeit(15000);

        // Then: Forfeit increments, active decrements
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.battlesForfeit());
        assertEquals(0, stats.activeBattles());
        assertEquals(15000.0, stats.avgBattleDurationMs(), 0.01);
    }

    @Test
    void recordBattleTimeout_incrementsCounterAndDecrementsActive() {
        // Given: One active battle
        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();

        // When: Battle times out
        metricsService.recordBattleTimeout(90000);

        // Then: Timeout increments, active decrements
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.battlesTimeout());
        assertEquals(0, stats.activeBattles());
        assertEquals(90000.0, stats.avgBattleDurationMs(), 0.01);
    }

    @Test
    void recordBattleAborted_incrementsCounterOnly() {
        // When: Battle aborted (during recovery)
        metricsService.recordBattleAborted();

        // Then: Aborted increments (gauges unchanged)
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.battlesAborted());
    }

    @Test
    void recordTurnPlayed_incrementsCounterAndRecordsDuration() {
        // When: Record multiple turns
        metricsService.recordTurnPlayed(100);
        metricsService.recordTurnPlayed(150);
        metricsService.recordTurnPlayed(200);

        // Then: Turns increment and duration is tracked
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(3, stats.turnsPlayed());
        assertEquals(150.0, stats.avgTurnDurationMs(), 1.0); // avg of 100, 150, 200
    }

    @Test
    void recordAttack_withoutCrit_incrementsAttackOnly() {
        // When: Record attack without crit
        metricsService.recordAttack(false);

        // Then: Attack increments, crit does not
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.attacksPerformed());
        assertEquals(0, stats.criticalHits());
    }

    @Test
    void recordAttack_withCrit_incrementsAttackAndCrit() {
        // When: Record attack with crit
        metricsService.recordAttack(true);

        // Then: Both attack and crit increment
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.attacksPerformed());
        assertEquals(1, stats.criticalHits());
    }

    @Test
    void recordDefend_incrementsDefendCounter() {
        // When: Record defend actions
        metricsService.recordDefend();
        metricsService.recordDefend();

        // Then: Defend increments
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(2, stats.defendsPerformed());
    }

    @Test
    void recordSpellCast_withoutCrit_incrementsSpellOnly() {
        // When: Record spell without crit
        metricsService.recordSpellCast(false);

        // Then: Spell increments, crit does not
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.spellsCast());
        assertEquals(0, stats.criticalHits());
    }

    @Test
    void recordSpellCast_withCrit_incrementsSpellAndCrit() {
        // When: Record spell with crit
        metricsService.recordSpellCast(true);

        // Then: Both spell and crit increment
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.spellsCast());
        assertEquals(1, stats.criticalHits());
    }

    @Test
    void recordCrits_fromBothAttacksAndSpells() {
        // When: Record crits from multiple sources
        metricsService.recordAttack(true);
        metricsService.recordAttack(false);
        metricsService.recordSpellCast(true);
        metricsService.recordSpellCast(true);

        // Then: Crits from both attacks and spells are counted
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(2, stats.attacksPerformed());
        assertEquals(2, stats.spellsCast());
        assertEquals(3, stats.criticalHits()); // 1 from attack + 2 from spells
    }

    @Test
    void setActiveBattles_manuallyAdjustsGauge() {
        // When: Manually set active battles (e.g., after recovery)
        metricsService.setActiveBattles(5);

        // Then: Gauge reflects new value
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(5, stats.activeBattles());
    }

    @Test
    void setPendingChallenges_manuallyAdjustsGauge() {
        // When: Manually set pending challenges
        metricsService.setPendingChallenges(3);

        // Then: Gauge reflects new value
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(3, stats.pendingChallenges());
    }

    @Test
    void getStats_returnsAllMetrics() {
        // Given: Various recorded metrics
        metricsService.recordChallengeCreated();
        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();
        metricsService.recordChallengeDeclined();

        // Accept 2 more challenges to balance the 3 battle endings
        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();
        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();

        metricsService.recordBattleCompleted(30000);
        metricsService.recordBattleForfeit(15000);
        metricsService.recordBattleTimeout(90000);
        // Note: recordBattleAborted() does NOT decrement activeBattles
        metricsService.recordBattleAborted();

        metricsService.recordTurnPlayed(100);
        metricsService.recordAttack(true);
        metricsService.recordDefend();
        metricsService.recordSpellCast(false);

        // When: Get stats
        BattleMetricsService.BattleStats stats = metricsService.getStats();

        // Then: All metrics are present
        assertEquals(4, stats.challengesCreated());
        assertEquals(3, stats.challengesAccepted());
        assertEquals(1, stats.challengesDeclined());
        assertEquals(1, stats.battlesCompleted());
        assertEquals(1, stats.battlesForfeit());
        assertEquals(1, stats.battlesTimeout());
        assertEquals(1, stats.battlesAborted());
        assertEquals(1, stats.turnsPlayed());
        assertEquals(1, stats.attacksPerformed());
        assertEquals(1, stats.defendsPerformed());
        assertEquals(1, stats.spellsCast());
        assertEquals(1, stats.criticalHits());
        assertEquals(0, stats.activeBattles()); // 3 accepted - 3 ended (completed/forfeit/timeout)
        assertEquals(0, stats.pendingChallenges()); // 4 created - 3 accepted - 1 declined
    }

    @Test
    void battleDuration_calculatesAverageAcrossMultipleBattles() {
        // Given: Active battle
        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();

        // When: Record multiple battles with different durations
        metricsService.recordBattleCompleted(10000);

        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();
        metricsService.recordBattleCompleted(20000);

        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();
        metricsService.recordBattleCompleted(30000);

        // Then: Average is calculated (10000 + 20000 + 30000) / 3 = 20000
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(20000.0, stats.avgBattleDurationMs(), 0.01);
    }

    @Test
    void turnDuration_calculatesAverageAcrossMultipleTurns() {
        // When: Record multiple turns with different durations
        metricsService.recordTurnPlayed(50);
        metricsService.recordTurnPlayed(100);
        metricsService.recordTurnPlayed(150);
        metricsService.recordTurnPlayed(200);

        // Then: Average is calculated (50 + 100 + 150 + 200) / 4 = 125
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(125.0, stats.avgTurnDurationMs(), 0.01);
    }

    @Test
    void fullBattleFlow_tracksAllMetricsCorrectly() {
        // Given: Complete battle flow
        // 1. Challenge created
        metricsService.recordChallengeCreated();
        assertEquals(1, metricsService.getStats().pendingChallenges());

        // 2. Challenge accepted
        metricsService.recordChallengeAccepted();
        assertEquals(0, metricsService.getStats().pendingChallenges());
        assertEquals(1, metricsService.getStats().activeBattles());

        // 3. Turns played
        metricsService.recordTurnPlayed(100);
        metricsService.recordAttack(false);

        metricsService.recordTurnPlayed(150);
        metricsService.recordDefend();

        metricsService.recordTurnPlayed(120);
        metricsService.recordSpellCast(true);

        // 4. Battle completed
        metricsService.recordBattleCompleted(25000);

        // Then: All metrics tracked
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.challengesCreated());
        assertEquals(1, stats.challengesAccepted());
        assertEquals(0, stats.activeBattles());
        assertEquals(1, stats.battlesCompleted());
        assertEquals(3, stats.turnsPlayed());
        assertEquals(1, stats.attacksPerformed());
        assertEquals(1, stats.defendsPerformed());
        assertEquals(1, stats.spellsCast());
        assertEquals(1, stats.criticalHits());
        assertEquals(123.33, stats.avgTurnDurationMs(), 0.5); // avg of 100, 150, 120
        assertEquals(25000.0, stats.avgBattleDurationMs(), 0.01);
    }

    @Test
    void gauges_canHandleNegativeDecrements() {
        // Given: Pending challenges at 0
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(0, stats.pendingChallenges());

        // When: Decline a challenge (decrements from 0)
        metricsService.recordChallengeDeclined();

        // Then: Gauge goes negative (AtomicInteger allows this)
        stats = metricsService.getStats();
        assertEquals(-1, stats.pendingChallenges());
    }

    @Test
    void recordBattleStarted_logsWithoutChangingMetrics() {
        // Given: Active battle
        metricsService.recordChallengeCreated();
        metricsService.recordChallengeAccepted();

        // When: Record battle started (optional logging)
        metricsService.recordBattleStarted();

        // Then: No metrics change (already counted in recordChallengeAccepted)
        BattleMetricsService.BattleStats stats = metricsService.getStats();
        assertEquals(1, stats.activeBattles());
    }
}
