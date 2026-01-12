package com.discordbot.battle.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates battle system configuration at startup and provides warnings for suboptimal settings.
 * Phase 12: Config & Tuning
 */
@Component
public class BattleConfigValidator {

    private static final Logger logger = LoggerFactory.getLogger(BattleConfigValidator.class);

    private final BattleProperties config;

    public BattleConfigValidator(BattleProperties config) {
        this.config = config;
    }

    @PostConstruct
    public void validateAndWarn() {
        if (!config.isEnabled()) {
            logger.info("Battle system is DISABLED (battle.enabled=false)");
            return;
        }

        logger.info("Battle system is ENABLED - validating configuration...");

        // Apply debug logging if battle.debug=true
        if (config.isDebug()) {
            enableDebugLogging();
            logger.debug("Battle debug mode ENABLED - verbose logging activated");
        }

        List<String> warnings = new ArrayList<>();
        List<String> info = new ArrayList<>();

        // Validate point-buy configuration
        validatePointBuy(warnings, info);

        // Validate combat settings
        validateCombat(warnings, info);

        // Validate progression settings
        validateProgression(warnings, info);

        // Validate scheduler settings
        validateScheduler(warnings, info);

        // Log results
        if (!warnings.isEmpty()) {
            logger.warn("Battle system configuration warnings:");
            warnings.forEach(w -> logger.warn("  - {}", w));
        }

        if (!info.isEmpty()) {
            logger.info("Battle system configuration notes:");
            info.forEach(i -> logger.info("  - {}", i));
        }

        if (warnings.isEmpty() && info.isEmpty()) {
            logger.info("Battle system configuration validated successfully - no issues found");
        }
    }

    private void validatePointBuy(List<String> warnings, List<String> info) {
        var pointBuy = config.getCharacter().getPointBuy();

        if (pointBuy.getTotalPoints() < 15) {
            warnings.add("Point-buy totalPoints is very low (" + pointBuy.getTotalPoints() + ") - characters will be weak");
        } else if (pointBuy.getTotalPoints() > 40) {
            warnings.add("Point-buy totalPoints is very high (" + pointBuy.getTotalPoints() + ") - characters will be overpowered");
        }

        if (pointBuy.getMinScore() < 3) {
            warnings.add("Point-buy minScore is extremely low (" + pointBuy.getMinScore() + ") - allows dump stats");
        }

        if (pointBuy.getMaxScore() > 18) {
            warnings.add("Point-buy maxScore is very high (" + pointBuy.getMaxScore() + ") - creates overpowered characters");
        }

        if (pointBuy.getTotalPoints() != 27) {
            info.add("Using non-standard point-buy total: " + pointBuy.getTotalPoints() + " (D&D 5e standard: 27)");
        }
    }

    private void validateCombat(List<String> warnings, List<String> info) {
        var combat = config.getCombat();
        var crit = combat.getCrit();

        // Crit threshold
        if (crit.getThreshold() < 18) {
            warnings.add("Crit threshold is very low (" + crit.getThreshold() + ") - high crit rate may reduce tactical play");
        } else if (crit.getThreshold() > 20) {
            warnings.add("Crit threshold is above 20 (" + crit.getThreshold() + ") - crits will be extremely rare");
        }

        // Crit multiplier
        if (crit.getMultiplier() < 1.5) {
            warnings.add("Crit multiplier is very low (" + crit.getMultiplier() + "x) - crits won't feel impactful");
        } else if (crit.getMultiplier() > 3.0) {
            warnings.add("Crit multiplier is very high (" + crit.getMultiplier() + "x) - creates extreme damage variance");
        }

        // Turn timeout
        if (combat.getTurn().getTimeoutSeconds() < 20) {
            warnings.add("Turn timeout is very short (" + combat.getTurn().getTimeoutSeconds() + "s) - may frustrate users");
        } else if (combat.getTurn().getTimeoutSeconds() > 300) {
            info.add("Turn timeout is very long (" + combat.getTurn().getTimeoutSeconds() + "s) - battles may drag on");
        }

        // Battle cooldown
        if (combat.getCooldownSeconds() == 0) {
            warnings.add("Battle cooldown is disabled - users can spam battles (only recommended for testing)");
        } else if (combat.getCooldownSeconds() > 600) {
            info.add("Battle cooldown is very long (" + combat.getCooldownSeconds() + "s) - may discourage battle participation");
        }

        // Concurrent battles
        if (combat.getMaxConcurrentPerGuild() < 10) {
            warnings.add("Max concurrent battles is very low (" + combat.getMaxConcurrentPerGuild() + ") - may hit limit on active servers");
        } else if (combat.getMaxConcurrentPerGuild() > 500) {
            warnings.add("Max concurrent battles is very high (" + combat.getMaxConcurrentPerGuild() + ") - may cause memory/performance issues");
        }

        // Defend AC bonus
        if (combat.getDefendAcBonus() < 1) {
            info.add("Defend AC bonus is very low (" + combat.getDefendAcBonus() + ") - defend action may be weak");
        } else if (combat.getDefendAcBonus() > 5) {
            warnings.add("Defend AC bonus is very high (+" + combat.getDefendAcBonus() + ") - may create defensive stalemates");
        }
    }

    private void validateProgression(List<String> warnings, List<String> info) {
        var progression = config.getProgression();
        var xp = progression.getXp();
        var chatXp = progression.getChatXp();

        // ELO K-factor
        if (progression.getElo().getK() < 10) {
            warnings.add("ELO K-factor is very low (" + progression.getElo().getK() + ") - ratings will change very slowly");
        } else if (progression.getElo().getK() > 64) {
            warnings.add("ELO K-factor is very high (" + progression.getElo().getK() + ") - ratings will be highly volatile");
        }

        // Battle XP balance
        long totalWinXp = xp.getBaseXp() + xp.getWinBonus();
        long chatXpAvg = chatXp.getBaseXp() + (chatXp.getBonusXpMax() / 2);

        if (totalWinXp > chatXpAvg * 10) {
            warnings.add("Battle XP is very high compared to chat XP - may encourage battle grinding over participation");
        }

        // Chat XP
        if (!chatXp.isEnabled()) {
            warnings.add("Chat XP is DISABLED - battle XP is the only progression source (slow progression)");
        }

        if (chatXp.getCooldownSeconds() < 20) {
            warnings.add("Chat XP cooldown is very short (" + chatXp.getCooldownSeconds() + "s) - may allow spam for XP");
        } else if (chatXp.getCooldownSeconds() > 300) {
            info.add("Chat XP cooldown is very long (" + chatXp.getCooldownSeconds() + "s) - progression will be slow");
        }

        // Auto-create character
        if (!chatXp.isAutoCreateCharacter()) {
            info.add("Auto-create character is disabled - users must run /create-character before earning XP");
        }
    }

    private void validateScheduler(List<String> warnings, List<String> info) {
        // Note: Scheduler config doesn't have a getter in BattleProperties
        // This is handled separately in BattleTimeoutScheduler and ChallengeCleanupScheduler
        // No validation needed here as they have their own config properties
    }

    /**
     * Generate a configuration health report.
     *
     * @return Configuration health status summary
     */
    public String generateHealthReport() {
        if (!config.isEnabled()) {
            return "Battle system is DISABLED";
        }

        StringBuilder report = new StringBuilder();
        report.append("Battle System Configuration Health Report\n");
        report.append("=========================================\n\n");

        // System status
        report.append(String.format("System: %s%s\n",
            config.isEnabled() ? "✓ ENABLED" : "✗ DISABLED",
            config.isDebug() ? " (debug mode)" : ""));

        // Point-buy
        var pointBuy = config.getCharacter().getPointBuy();
        report.append(String.format("\nPoint-Buy: %d points (%d-%d scores)\n",
            pointBuy.getTotalPoints(), pointBuy.getMinScore(), pointBuy.getMaxScore()));

        // Combat
        var combat = config.getCombat();
        report.append(String.format("\nCombat:\n"));
        report.append(String.format("  Crit: d20 >= %d (×%.1f damage)\n",
            combat.getCrit().getThreshold(), combat.getCrit().getMultiplier()));
        report.append(String.format("  Turn timeout: %ds\n", combat.getTurn().getTimeoutSeconds()));
        report.append(String.format("  Battle cooldown: %ds\n", combat.getCooldownSeconds()));
        report.append(String.format("  Max concurrent/guild: %d\n", combat.getMaxConcurrentPerGuild()));

        // Progression
        var progression = config.getProgression();
        report.append(String.format("\nProgression:\n"));
        report.append(String.format("  ELO K-factor: %d\n", progression.getElo().getK()));
        report.append(String.format("  Battle XP: %d base, +%d win, +%d draw\n",
            progression.getXp().getBaseXp(),
            progression.getXp().getWinBonus(),
            progression.getXp().getDrawBonus()));

        var chatXp = progression.getChatXp();
        report.append(String.format("  Chat XP: %s (%d-%d per msg, %ds cooldown)\n",
            chatXp.isEnabled() ? "ENABLED" : "DISABLED",
            chatXp.getBaseXp(),
            chatXp.getBaseXp() + chatXp.getBonusXpMax(),
            chatXp.getCooldownSeconds()));

        return report.toString();
    }
    
    /**
     * Enables DEBUG logging for all battle system packages when battle.debug=true.
     * This allows logger.debug() calls to be visible without manually changing application.properties.
     */
    private void enableDebugLogging() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Set DEBUG level for all battle packages
        ch.qos.logback.classic.Logger battleLogger = loggerContext.getLogger("com.discordbot.battle");
        battleLogger.setLevel(Level.DEBUG);
        
        logger.info("Battle debug logging enabled - set com.discordbot.battle to DEBUG level");
    }
}
