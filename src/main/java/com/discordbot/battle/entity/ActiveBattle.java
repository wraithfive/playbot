package com.discordbot.battle.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/**
 * Represents an in-memory active (or pending) battle between two player characters.
 * <p>
 * Persistence is intentionally deferred until the battle system stabilizes. Instances are
 * stored in an in-memory cache (service layer) and discarded when ENDED.
 * </p>
 */
public class ActiveBattle {

    /** Unique battle identifier (UUID string for easy logging). */
    private final String id;
    /** Discord guild/server ID. */
    private final String guildId;
    /** Challenger user ID (initiator). */
    private final String challengerId;
    /** Opponent user ID (target of challenge). */
    private final String opponentId;
    /** Current status of the battle. */
    private BattleStatus status;
    /** Epoch millis when challenge was created. */
    private final long createdAt;
    /** Epoch millis when battle was started (accepted). */
    private Long startedAt;
    /** Epoch millis when battle ended. */
    private Long endedAt;
    /** Whose turn it is (user ID). */
    private String currentTurnUserId;
    /** Current HP of challenger. */
    private int challengerHp;
    /** Current HP of opponent. */
    private int opponentHp;
    /** Simple chronological log of events. */
    private final List<BattleLogEntry> logEntries = new ArrayList<>();
    /** Winner user ID once ended (null until ENDED). */
    private String winnerUserId;
    /** Current turn number (starts at 1 when battle starts). */
    private int turnNumber = 0;
    /** Temporary AC bonus from defend action (cleared after one turn). */
    private int tempAcBonus = 0;
    /** User ID who has the temporary AC bonus (null if none). */
    private String tempAcBonusUserId = null;
    /** Last action timestamp in epoch millis (for turn timeout detection). */
    private Long lastActionAt;

    // Dice suppliers (test seam). Defaults to ThreadLocalRandom but can be overridden in tests.
    private transient IntSupplier d20Supplier = () -> ThreadLocalRandom.current().nextInt(1, 21);
    private transient IntSupplier d6Supplier = () -> ThreadLocalRandom.current().nextInt(1, 7);

    /** Battle lifecycle states. */
    public enum BattleStatus { PENDING, ACTIVE, ENDED }

    /** Log entry representing a single battle event. */
    public static class BattleLogEntry {
        private final long timestamp;
        private final String message;

        public BattleLogEntry(long timestamp, String message) {
            this.timestamp = timestamp;
            this.message = message;
        }

        public long getTimestamp() { return timestamp; }
        public String getMessage() { return message; }
    }

    private ActiveBattle(String id,
                         String guildId,
                         String challengerId,
                         String opponentId,
                         BattleStatus status,
                         long createdAt) {
        this.id = id;
        this.guildId = guildId;
        this.challengerId = challengerId;
        this.opponentId = opponentId;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** Factory for a new pending challenge. */
    public static ActiveBattle createPending(String guildId, String challengerId, String opponentId) {
        return new ActiveBattle(UUID.randomUUID().toString(), guildId, challengerId, opponentId, BattleStatus.PENDING, System.currentTimeMillis());
    }

    /** Start the battle (accept). Initializes HP and first turn. */
    public void start(int challengerHp, int opponentHp) {
        if (status != BattleStatus.PENDING) {
            throw new IllegalStateException("Battle can only be started from PENDING state");
        }
        this.status = BattleStatus.ACTIVE;
        this.startedAt = System.currentTimeMillis();
        this.lastActionAt = System.currentTimeMillis();
        this.challengerHp = challengerHp;
        this.opponentHp = opponentHp;
        this.turnNumber = 1;
        // Challenger goes first for MVP. Could randomize later.
        this.currentTurnUserId = challengerId;
        addLog("Battle started: " + challengerId + " vs " + opponentId + " (CHP=" + challengerHp + ", OHP=" + opponentHp + ")");
    }

    /** Decline the battle before start. */
    public void decline(String byUserId) {
        if (status != BattleStatus.PENDING) {
            throw new IllegalStateException("Cannot decline - battle already started or ended");
        }
        this.status = BattleStatus.ENDED;
        this.endedAt = System.currentTimeMillis();
        addLog("Challenge declined by " + byUserId);
    }

    /** Apply damage to the target. */
    public void applyDamageToOpponent(int dmg) {
        opponentHp = Math.max(0, opponentHp - dmg);
    }
    public void applyDamageToChallenger(int dmg) {
        challengerHp = Math.max(0, challengerHp - dmg);
    }

    /** Advance turn to the other player. */
    public void advanceTurn() {
        if (status != BattleStatus.ACTIVE) return;
        // Clear temp AC bonus when advancing back to the defender's turn
        // (defend bonus applies during the opponent's next attack, then clears when advancing back to defender's turn)
        if (tempAcBonusUserId != null && !tempAcBonusUserId.equals(currentTurnUserId)) {
            tempAcBonus = 0;
            tempAcBonusUserId = null;
        }
        currentTurnUserId = currentTurnUserId.equals(challengerId) ? opponentId : challengerId;
        turnNumber++;
    }

    /** End battle and set winner. */
    public void end(String winnerUserId) {
        this.status = BattleStatus.ENDED;
        this.winnerUserId = winnerUserId;
        this.endedAt = System.currentTimeMillis();
        addLog("Battle ended. Winner: " + winnerUserId);
    }

    /** Roll a d20 (utility). */
    public int rollD20() { return d20Supplier.getAsInt(); }

    /** Roll weapon damage 1d6 (utility). */
    public int rollD6() { return d6Supplier.getAsInt(); }

    /**
     * Overrides dice suppliers for testing to achieve deterministic outcomes.
     * Pass null to reset to default random behavior for either die.
     */
    public void setTestDiceSuppliers(IntSupplier d20Supplier, IntSupplier d6Supplier) {
        if (d20Supplier != null) this.d20Supplier = d20Supplier; else this.d20Supplier = () -> ThreadLocalRandom.current().nextInt(1, 21);
        if (d6Supplier != null) this.d6Supplier = d6Supplier; else this.d6Supplier = () -> ThreadLocalRandom.current().nextInt(1, 7);
    }

    public void addLog(String message) {
        logEntries.add(new BattleLogEntry(System.currentTimeMillis(), message));
    }

    /** Apply temporary AC bonus from defend action. */
    public void applyTempAcBonus(String userId, int bonus) {
        this.tempAcBonus = bonus;
        this.tempAcBonusUserId = userId;
    }

    /** Get effective AC for a user (including temporary bonuses). */
    public int getEffectiveAcBonus(String userId) {
        if (tempAcBonusUserId != null && tempAcBonusUserId.equals(userId)) {
            return tempAcBonus;
        }
        return 0;
    }

    /** Reset last action timestamp (called when a turn action is performed). */
    public void resetLastActionAt() {
        this.lastActionAt = System.currentTimeMillis();
    }

    // Getters
    public String getId() { return id; }
    public String getGuildId() { return guildId; }
    public String getChallengerId() { return challengerId; }
    public String getOpponentId() { return opponentId; }
    public BattleStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public Long getStartedAt() { return startedAt; }
    public Long getEndedAt() { return endedAt; }
    public String getCurrentTurnUserId() { return currentTurnUserId; }
    public int getChallengerHp() { return challengerHp; }
    public int getOpponentHp() { return opponentHp; }
    public List<BattleLogEntry> getLogEntries() { return logEntries; }
    public String getWinnerUserId() { return winnerUserId; }
    public int getTurnNumber() { return turnNumber; }
    public int getTempAcBonus() { return tempAcBonus; }
    public String getTempAcBonusUserId() { return tempAcBonusUserId; }
    public Long getLastActionAt() { return lastActionAt; }

    // Convenience checks
    public boolean isPending() { return status == BattleStatus.PENDING; }
    public boolean isActive() { return status == BattleStatus.ACTIVE; }
    public boolean isEnded() { return status == BattleStatus.ENDED; }
}
