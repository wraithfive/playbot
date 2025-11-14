package com.discordbot.battle.service;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.BattleSession;
import com.discordbot.battle.repository.BattleSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 7: Edge Cases & Recovery
 *
 * Handles battle recovery on bot startup and stale battle cleanup.
 * Scans for battles that were active during bot shutdown and aborts them gracefully.
 */
@Service
public class BattleRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(BattleRecoveryService.class);

    private final BattleSessionRepository sessionRepository;
    private final BattleProperties battleProperties;

    public BattleRecoveryService(BattleSessionRepository sessionRepository,
                                BattleProperties battleProperties) {
        this.sessionRepository = sessionRepository;
        this.battleProperties = battleProperties;
    }

    /**
     * Run recovery process when application is fully started.
     * This runs AFTER all beans are initialized and the bot is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStaleBattlesOnStartup() {
        if (!battleProperties.isEnabled()) {
            logger.debug("Battle system disabled, skipping recovery");
            return;
        }

        logger.info("Starting battle recovery process...");

        try {
            // Find all active/pending battles from previous session
            List<BattleSession> activeBattles = sessionRepository.findActiveBattles();

            if (activeBattles.isEmpty()) {
                logger.info("No stale battles found. Recovery complete.");
                return;
            }

            logger.warn("Found {} stale battle(s) from previous session", activeBattles.size());

            int abortedCount = 0;
            for (BattleSession battle : activeBattles) {
                try {
                    abortStaleBattle(battle);
                    abortedCount++;
                } catch (Exception e) {
                    logger.error("Failed to abort stale battle {}: {}", battle.getId(), e.getMessage(), e);
                }
            }

            logger.info("Battle recovery complete: {} battle(s) aborted", abortedCount);

        } catch (Exception e) {
            logger.error("Battle recovery failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Abort a stale battle (graceful termination without rewards).
     * Sets status to ABORTED and updates timestamps.
     */
    @Transactional
    public void abortStaleBattle(BattleSession battle) {
        battle.setStatus(BattleSession.BattleStatus.ABORTED);
        battle.setEndedAt(LocalDateTime.now());
        sessionRepository.save(battle);

        logger.info("Aborted stale battle: id={} guild={} challenger={} opponent={} status={} turnNumber={}",
            battle.getId(),
            battle.getGuildId(),
            battle.getChallengerId(),
            battle.getOpponentId(),
            battle.getStatus(),
            battle.getTurnNumber()
        );
    }

    /**
     * Find and abort battles that have exceeded turn timeout threshold.
     * Can be called from a scheduled task or on-demand.
     */
    @Transactional
    public int cleanupTimedOutBattles() {
        int timeoutSeconds = battleProperties.getCombat().getTurn().getTimeoutSeconds();
        // Use a longer threshold for cleanup (e.g., 2x turn timeout)
        int cleanupThresholdSeconds = timeoutSeconds * 2;
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(cleanupThresholdSeconds);

        List<BattleSession> staleBattles = sessionRepository.findStaleBattles(threshold);

        if (staleBattles.isEmpty()) {
            return 0;
        }

        logger.warn("Found {} timed-out battle(s) to clean up", staleBattles.size());

        int abortedCount = 0;
        for (BattleSession battle : staleBattles) {
            try {
                abortStaleBattle(battle);
                abortedCount++;
            } catch (Exception e) {
                logger.error("Failed to abort timed-out battle {}: {}", battle.getId(), e.getMessage(), e);
            }
        }

        return abortedCount;
    }

    /**
     * Get all active battles from database (for admin/monitoring).
     */
    public List<BattleSession> getAllActiveBattles() {
        return sessionRepository.findActiveBattles();
    }

    /**
     * Get count of battles by status (for metrics/monitoring).
     */
    public long countBattlesByStatus(BattleSession.BattleStatus status) {
        return sessionRepository.findByStatus(status).size();
    }
}
