package com.discordbot.battle.scheduler;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled job to automatically end battles that have exceeded the turn timeout.
 * Runs periodically to check all active battles and enforce timeout rules.
 *
 * Configuration:
 * - battle.scheduler.timeout.enabled (default: true) - Enable/disable timeout checker
 * - battle.scheduler.timeout.checkIntervalMs (default: 30000) - How often to check in milliseconds
 * - battle.combat.turn.timeoutSeconds - Timeout duration per turn
 */
@Component
@ConditionalOnProperty(
    prefix = "battle.scheduler.timeout",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class BattleTimeoutScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BattleTimeoutScheduler.class);

    private final BattleService battleService;
    private final BattleProperties battleProperties;

    public BattleTimeoutScheduler(BattleService battleService, BattleProperties battleProperties) {
        this.battleService = battleService;
        this.battleProperties = battleProperties;
        logger.info("BattleTimeoutScheduler initialized (timeout check enabled)");
    }

    /**
     * Check for and timeout stale battles.
     * Runs at fixed rate configured by battle.scheduler.timeout.checkIntervalMs.
     * Default: every 30 seconds.
     */
    @Scheduled(fixedRateString = "${battle.scheduler.timeout.checkIntervalMs:30000}")
    public void checkAndTimeoutStaleBattles() {
        if (!battleProperties.isEnabled()) {
            // Don't run if battle system is disabled
            return;
        }

        AtomicInteger timedOutCount = new AtomicInteger(0);

        try {
            // Get all active battles from BattleService
            Collection<ActiveBattle> activeBattles = battleService.getAllActiveBattles();

            for (ActiveBattle battle : activeBattles) {
                if (battleService.checkTurnTimeout(battle)) {
                    try {
                        battleService.timeoutTurn(battle);
                        timedOutCount.incrementAndGet();
                        logger.info("Battle timed out: battleId={} currentTurn={} guildId={}",
                                   battle.getId(), battle.getCurrentTurnUserId(), battle.getGuildId());
                    } catch (Exception e) {
                        logger.error("Failed to timeout battle {}: {}", battle.getId(), e.getMessage(), e);
                    }
                }
            }

            if (timedOutCount.get() > 0) {
                logger.info("Timeout checker processed {} stale battle(s)", timedOutCount.get());
            } else {
                logger.debug("Timeout checker ran, no stale battles found");
            }

        } catch (Exception e) {
            logger.error("Error in battle timeout checker: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup expired challenges periodically.
     * Runs at a slower rate than timeout checker since expired challenges are less urgent.
     * Default: every 2 minutes.
     */
    @Scheduled(fixedRateString = "${battle.scheduler.cleanup.checkIntervalMs:120000}")
    public void cleanupExpiredChallenges() {
        if (!battleProperties.isEnabled()) {
            return;
        }

        try {
            int expiredCount = battleService.cleanUpExpiredChallenges();
            if (expiredCount > 0) {
                logger.info("Challenge cleanup removed {} expired challenge(s)", expiredCount);
            } else {
                logger.debug("Challenge cleanup ran, no expired challenges found");
            }
        } catch (Exception e) {
            logger.error("Error in challenge cleanup: {}", e.getMessage(), e);
        }
    }
}
