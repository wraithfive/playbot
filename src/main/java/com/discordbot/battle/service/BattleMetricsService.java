package com.discordbot.battle.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 8: Monitoring & Logging
 *
 * Provides metrics collection for the battle system using Micrometer.
 * Tracks counters, gauges, and timers for monitoring battle system health and usage.
 */
@Service
public class BattleMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(BattleMetricsService.class);

    // Counters
    private final Counter challengesCreated;
    private final Counter challengesAccepted;
    private final Counter challengesDeclined;
    private final Counter battlesCompleted;
    private final Counter battlesForfeit;
    private final Counter battlesTimeout;
    private final Counter battlesAborted;
    private final Counter turnsPlayed;
    private final Counter attacksPerformed;
    private final Counter defendsPerformed;
    private final Counter spellsCast;
    private final Counter criticalHits;

    // Gauges (using AtomicInteger for thread-safe updates)
    private final AtomicInteger activeBattles = new AtomicInteger(0);
    private final AtomicInteger pendingChallenges = new AtomicInteger(0);

    // Timers
    private final Timer turnDuration;
    private final Timer battleDuration;

    public BattleMetricsService(MeterRegistry registry) {
        // Initialize counters
        challengesCreated = Counter.builder("battle.challenges.created")
            .description("Total number of duel challenges created")
            .tag("system", "battle")
            .register(registry);

        challengesAccepted = Counter.builder("battle.challenges.accepted")
            .description("Total number of duel challenges accepted")
            .tag("system", "battle")
            .register(registry);

        challengesDeclined = Counter.builder("battle.challenges.declined")
            .description("Total number of duel challenges declined")
            .tag("system", "battle")
            .register(registry);

        battlesCompleted = Counter.builder("battle.completed")
            .description("Total number of battles completed (victory/draw)")
            .tag("system", "battle")
            .register(registry);

        battlesForfeit = Counter.builder("battle.forfeit")
            .description("Total number of battles ended by forfeit")
            .tag("system", "battle")
            .register(registry);

        battlesTimeout = Counter.builder("battle.timeout")
            .description("Total number of battles ended by timeout")
            .tag("system", "battle")
            .register(registry);

        battlesAborted = Counter.builder("battle.aborted")
            .description("Total number of battles aborted (recovery/errors)")
            .tag("system", "battle")
            .register(registry);

        turnsPlayed = Counter.builder("battle.turns.played")
            .description("Total number of combat turns resolved")
            .tag("system", "battle")
            .register(registry);

        attacksPerformed = Counter.builder("battle.actions.attack")
            .description("Total number of attack actions")
            .tag("system", "battle")
            .register(registry);

        defendsPerformed = Counter.builder("battle.actions.defend")
            .description("Total number of defend actions")
            .tag("system", "battle")
            .register(registry);

        spellsCast = Counter.builder("battle.actions.spell")
            .description("Total number of spells cast")
            .tag("system", "battle")
            .register(registry);

        criticalHits = Counter.builder("battle.combat.crits")
            .description("Total number of critical hits")
            .tag("system", "battle")
            .register(registry);

        // Initialize gauges
        Gauge.builder("battle.sessions.active", activeBattles, AtomicInteger::get)
            .description("Current number of active battles")
            .tag("system", "battle")
            .register(registry);

        Gauge.builder("battle.sessions.pending", pendingChallenges, AtomicInteger::get)
            .description("Current number of pending challenges")
            .tag("system", "battle")
            .register(registry);

        // Initialize timers
        turnDuration = Timer.builder("battle.turn.duration")
            .description("Time taken to resolve a combat turn")
            .tag("system", "battle")
            .register(registry);

        battleDuration = Timer.builder("battle.duration")
            .description("Total duration of battles from start to end")
            .tag("system", "battle")
            .register(registry);

        logger.info("BattleMetricsService initialized with Micrometer");
    }

    // ============ Challenge Metrics ============

    public void recordChallengeCreated() {
        challengesCreated.increment();
        pendingChallenges.incrementAndGet();
    }

    public void recordChallengeAccepted() {
        challengesAccepted.increment();
        pendingChallenges.decrementAndGet();
        activeBattles.incrementAndGet();
    }

    public void recordChallengeDeclined() {
        challengesDeclined.increment();
        pendingChallenges.decrementAndGet();
    }

    public void recordChallengeExpired() {
        // Challenges that expire without acceptance/decline
        pendingChallenges.decrementAndGet();
    }

    // ============ Battle Lifecycle Metrics ============

    public void recordBattleStarted() {
        // Track when a battle actually starts (after challenge acceptance)
        // Note: recordChallengeAccepted() already increments activeBattles
        // This method is for additional tracking if needed
        logger.debug("Battle started (active battles: {})", activeBattles.get());
    }

    public void recordBattleCompleted(long durationMs) {
        battlesCompleted.increment();
        activeBattles.decrementAndGet();
        battleDuration.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordBattleForfeit(long durationMs) {
        battlesForfeit.increment();
        activeBattles.decrementAndGet();
        battleDuration.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordBattleTimeout(long durationMs) {
        battlesTimeout.increment();
        activeBattles.decrementAndGet();
        battleDuration.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordBattleAborted() {
        battlesAborted.increment();
        // Note: We may not have accurate active/pending counts during recovery
    }

    // ============ Combat Action Metrics ============

    public void recordTurnPlayed(long turnDurationMs) {
        turnsPlayed.increment();
        turnDuration.record(turnDurationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordAttack(boolean crit) {
        attacksPerformed.increment();
        if (crit) {
            criticalHits.increment();
        }
    }

    public void recordDefend() {
        defendsPerformed.increment();
    }

    public void recordSpellCast(boolean crit) {
        spellsCast.increment();
        if (crit) {
            criticalHits.increment();
        }
    }

    // ============ Gauge Adjustments (for manual corrections) ============

    public void setActiveBattles(int count) {
        activeBattles.set(count);
    }

    public void setPendingChallenges(int count) {
        pendingChallenges.set(count);
    }

    // ============ Metrics Retrieval (for /battle-stats command) ============

    public BattleStats getStats() {
        return new BattleStats(
            (long) challengesCreated.count(),
            (long) challengesAccepted.count(),
            (long) challengesDeclined.count(),
            (long) battlesCompleted.count(),
            (long) battlesForfeit.count(),
            (long) battlesTimeout.count(),
            (long) battlesAborted.count(),
            (long) turnsPlayed.count(),
            (long) attacksPerformed.count(),
            (long) defendsPerformed.count(),
            (long) spellsCast.count(),
            (long) criticalHits.count(),
            activeBattles.get(),
            pendingChallenges.get(),
            turnDuration.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
            battleDuration.mean(java.util.concurrent.TimeUnit.MILLISECONDS)
        );
    }

    /**
     * Battle statistics snapshot
     */
    public record BattleStats(
        long challengesCreated,
        long challengesAccepted,
        long challengesDeclined,
        long battlesCompleted,
        long battlesForfeit,
        long battlesTimeout,
        long battlesAborted,
        long turnsPlayed,
        long attacksPerformed,
        long defendsPerformed,
        long spellsCast,
        long criticalHits,
        int activeBattles,
        int pendingChallenges,
        double avgTurnDurationMs,
        double avgBattleDurationMs
    ) {}
}
