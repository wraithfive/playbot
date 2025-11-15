package com.discordbot.battle.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persistent battle session entity for recovery and audit purposes.
 * Stores battle state to survive bot restarts and enable stale battle cleanup.
 *
 * Phase 7: Edge Cases & Recovery
 */
@Entity
@Table(name = "battle_session",
       indexes = {
           @Index(name = "idx_battle_session_status", columnList = "status"),
           @Index(name = "idx_battle_session_guild", columnList = "guildId"),
           @Index(name = "idx_battle_session_last_action", columnList = "lastActionAt")
       })
public class BattleSession {

    @Id
    @Column(nullable = false, length = 36)
    private String id; // UUID from ActiveBattle

    @Column(nullable = false, length = 20)
    private String guildId;

    @Column(nullable = false, length = 20)
    private String challengerId;

    @Column(nullable = false, length = 20)
    private String opponentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BattleStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;

    @Column(length = 20)
    private String currentTurnUserId;

    @Column(nullable = false)
    private int challengerHp;

    @Column(nullable = false)
    private int opponentHp;

    @Column(nullable = false)
    private int challengerMaxHp;

    @Column(nullable = false)
    private int opponentMaxHp;

    @Column(length = 20)
    private String winnerUserId;

    @Column(nullable = false)
    private int turnNumber = 0;

    @Column(nullable = false)
    private int tempAcBonus = 0;

    @Column(length = 20)
    private String tempAcBonusUserId;

    @Column
    private LocalDateTime lastActionAt;

    /**
     * Battle lifecycle states.
     */
    public enum BattleStatus {
        PENDING,  // Challenge issued but not accepted
        ACTIVE,   // Battle in progress
        COMPLETED, // Battle ended normally (victory/draw)
        ABORTED   // Battle terminated abnormally (timeout, recovery, error)
    }

    /**
     * Default constructor for JPA
     */
    protected BattleSession() {
    }

    /**
     * Create a new battle session from an ActiveBattle
     */
    public BattleSession(String id, String guildId, String challengerId, String opponentId, BattleStatus status) {
        this.id = id;
        this.guildId = guildId;
        this.challengerId = challengerId;
        this.opponentId = opponentId;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Create from ActiveBattle snapshot
     */
    public static BattleSession fromActiveBattle(ActiveBattle battle) {
        BattleSession session = new BattleSession(
            battle.getId(),
            battle.getGuildId(),
            battle.getChallengerId(),
            battle.getOpponentId(),
            mapStatus(battle.getStatus())
        );

        session.challengerHp = battle.getChallengerHp();
        session.opponentHp = battle.getOpponentHp();
        session.challengerMaxHp = battle.getChallengerMaxHp();
        session.opponentMaxHp = battle.getOpponentMaxHp();
        session.currentTurnUserId = battle.getCurrentTurnUserId();
        session.turnNumber = battle.getTurnNumber();
        session.tempAcBonus = battle.getTempAcBonus();
        session.tempAcBonusUserId = battle.getTempAcBonusUserId();
        session.winnerUserId = battle.getWinnerUserId();

        if (battle.getStartedAt() != null) {
            session.startedAt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(battle.getStartedAt()),
                java.time.ZoneId.systemDefault()
            );
        }

        if (battle.getEndedAt() != null) {
            session.endedAt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(battle.getEndedAt()),
                java.time.ZoneId.systemDefault()
            );
        }

        if (battle.getLastActionAt() != null) {
            session.lastActionAt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(battle.getLastActionAt()),
                java.time.ZoneId.systemDefault()
            );
        }

        return session;
    }

    /**
     * Map ActiveBattle.BattleStatus to BattleSession.BattleStatus
     */
    private static BattleStatus mapStatus(ActiveBattle.BattleStatus activeStatus) {
        return switch (activeStatus) {
            case PENDING -> BattleStatus.PENDING;
            case ACTIVE -> BattleStatus.ACTIVE;
            case ENDED -> BattleStatus.COMPLETED;
        };
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getChallengerId() {
        return challengerId;
    }

    public String getOpponentId() {
        return opponentId;
    }

    public BattleStatus getStatus() {
        return status;
    }

    public void setStatus(BattleStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public String getCurrentTurnUserId() {
        return currentTurnUserId;
    }

    public void setCurrentTurnUserId(String currentTurnUserId) {
        this.currentTurnUserId = currentTurnUserId;
    }

    public int getChallengerHp() {
        return challengerHp;
    }

    public void setChallengerHp(int challengerHp) {
        this.challengerHp = challengerHp;
    }

    public int getOpponentHp() {
        return opponentHp;
    }

    public void setOpponentHp(int opponentHp) {
        this.opponentHp = opponentHp;
    }

    public int getChallengerMaxHp() {
        return challengerMaxHp;
    }

    public void setChallengerMaxHp(int challengerMaxHp) {
        this.challengerMaxHp = challengerMaxHp;
    }

    public int getOpponentMaxHp() {
        return opponentMaxHp;
    }

    public void setOpponentMaxHp(int opponentMaxHp) {
        this.opponentMaxHp = opponentMaxHp;
    }

    public String getWinnerUserId() {
        return winnerUserId;
    }

    public void setWinnerUserId(String winnerUserId) {
        this.winnerUserId = winnerUserId;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public int getTempAcBonus() {
        return tempAcBonus;
    }

    public void setTempAcBonus(int tempAcBonus) {
        this.tempAcBonus = tempAcBonus;
    }

    public String getTempAcBonusUserId() {
        return tempAcBonusUserId;
    }

    public void setTempAcBonusUserId(String tempAcBonusUserId) {
        this.tempAcBonusUserId = tempAcBonusUserId;
    }

    public LocalDateTime getLastActionAt() {
        return lastActionAt;
    }

    public void setLastActionAt(LocalDateTime lastActionAt) {
        this.lastActionAt = lastActionAt;
    }
}
