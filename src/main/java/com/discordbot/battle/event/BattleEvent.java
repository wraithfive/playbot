package com.discordbot.battle.event;

import org.slf4j.Logger;

/**
 * Phase 8: Monitoring & Logging
 *
 * Base class for structured battle events.
 * All battle events follow a consistent logging pattern with structured data.
 */
public abstract class BattleEvent {

    /**
     * Log this event with structured data
     */
    public abstract void log(Logger logger);

    /**
     * Get the event type for metrics/filtering
     */
    public abstract String getEventType();

    /**
     * Format key-value pairs for structured logging
     */
    protected String formatKV(String key, Object value) {
        if (value == null) {
            return key + "=null";
        }
        if (value instanceof String) {
            return key + "=\"" + value + "\"";
        }
        return key + "=" + value;
    }

    /**
     * Challenge Created Event
     */
    public static class ChallengeCreated extends BattleEvent {
        private final String battleId;
        private final String guildId;
        private final String initiatorId;
        private final String opponentId;

        public ChallengeCreated(String battleId, String guildId, String initiatorId, String opponentId) {
            this.battleId = battleId;
            this.guildId = guildId;
            this.initiatorId = initiatorId;
            this.opponentId = opponentId;
        }

        @Override
        public void log(Logger logger) {
            logger.info("battle.challenge.created {} {} {} {}",
                formatKV("battleId", battleId),
                formatKV("guildId", guildId),
                formatKV("initiator", initiatorId),
                formatKV("opponent", opponentId));
        }

        @Override
        public String getEventType() {
            return "battle.challenge.created";
        }
    }

    /**
     * Challenge Accepted Event
     */
    public static class ChallengeAccepted extends BattleEvent {
        private final String battleId;
        private final String guildId;
        private final String acceptingUserId;

        public ChallengeAccepted(String battleId, String guildId, String acceptingUserId) {
            this.battleId = battleId;
            this.guildId = guildId;
            this.acceptingUserId = acceptingUserId;
        }

        @Override
        public void log(Logger logger) {
            logger.info("battle.challenge.accepted {} {} {}",
                formatKV("battleId", battleId),
                formatKV("guildId", guildId),
                formatKV("acceptedBy", acceptingUserId));
        }

        @Override
        public String getEventType() {
            return "battle.challenge.accepted";
        }
    }

    /**
     * Turn Resolved Event
     */
    public static class TurnResolved extends BattleEvent {
        private final String battleId;
        private final int turnNumber;
        private final String actorId;
        private final String action;
        private final int damage;
        private final boolean crit;
        private final int hpActor;
        private final int hpTarget;

        public TurnResolved(String battleId, int turnNumber, String actorId, String action,
                           int damage, boolean crit, int hpActor, int hpTarget) {
            this.battleId = battleId;
            this.turnNumber = turnNumber;
            this.actorId = actorId;
            this.action = action;
            this.damage = damage;
            this.crit = crit;
            this.hpActor = hpActor;
            this.hpTarget = hpTarget;
        }

        @Override
        public void log(Logger logger) {
            logger.info("battle.turn.resolved {} {} {} {} {} {} {} {}",
                formatKV("battleId", battleId),
                formatKV("turn", turnNumber),
                formatKV("actor", actorId),
                formatKV("action", action),
                formatKV("damage", damage),
                formatKV("crit", crit),
                formatKV("hpActor", hpActor),
                formatKV("hpTarget", hpTarget));
        }

        @Override
        public String getEventType() {
            return "battle.turn.resolved";
        }
    }

    /**
     * Battle Timeout Event
     */
    public static class BattleTimeout extends BattleEvent {
        private final String battleId;
        private final String timeoutUserId;
        private final String winnerId;

        public BattleTimeout(String battleId, String timeoutUserId, String winnerId) {
            this.battleId = battleId;
            this.timeoutUserId = timeoutUserId;
            this.winnerId = winnerId;
        }

        @Override
        public void log(Logger logger) {
            logger.warn("battle.timeout {} {} {}",
                formatKV("battleId", battleId),
                formatKV("timeoutUser", timeoutUserId),
                formatKV("winner", winnerId));
        }

        @Override
        public String getEventType() {
            return "battle.timeout";
        }
    }

    /**
     * Battle Completed Event
     */
    public static class BattleCompleted extends BattleEvent {
        private final String battleId;
        private final String winnerId;
        private final boolean draw;
        private final int finalTurns;
        private final long durationMs;

        public BattleCompleted(String battleId, String winnerId, boolean draw, int finalTurns, long durationMs) {
            this.battleId = battleId;
            this.winnerId = winnerId;
            this.draw = draw;
            this.finalTurns = finalTurns;
            this.durationMs = durationMs;
        }

        @Override
        public void log(Logger logger) {
            logger.info("battle.completed {} {} {} {} {}",
                formatKV("battleId", battleId),
                formatKV("winner", winnerId),
                formatKV("draw", draw),
                formatKV("turns", finalTurns),
                formatKV("durationMs", durationMs));
        }

        @Override
        public String getEventType() {
            return "battle.completed";
        }
    }

    /**
     * Battle Aborted Event
     */
    public static class BattleAborted extends BattleEvent {
        private final String battleId;
        private final String reason;

        public BattleAborted(String battleId, String reason) {
            this.battleId = battleId;
            this.reason = reason;
        }

        @Override
        public void log(Logger logger) {
            logger.warn("battle.aborted {} {}",
                formatKV("battleId", battleId),
                formatKV("reason", reason));
        }

        @Override
        public String getEventType() {
            return "battle.aborted";
        }
    }
}
