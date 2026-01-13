package com.discordbot.battle.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent log entry for a single turn action in a battle.
 * Provides an immutable audit trail of all combat actions.
 */
@Entity
@Table(name = "battle_turn_log",
       indexes = {
           @Index(name = "idx_battle_turn_log_battle_id", columnList = "battle_id"),
           @Index(name = "idx_battle_turn_log_battle_turn", columnList = "battle_id, turn_number"),
           @Index(name = "idx_battle_turn_log_guild_id", columnList = "guild_id")
       })
public class BattleTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "battle_id", nullable = false)
    private String battleId;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "turn_number", nullable = false)
    private int turnNumber;

    @Column(name = "actor_user_id", nullable = false)
    private String actorUserId;

    @Column(name = "action_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Column(name = "target_user_id")
    private String targetUserId;

    @Column(name = "ability_id")
    private Long abilityId;

    @Column(name = "damage_dealt")
    private Integer damageDealt = 0;

    @Column(name = "healing_done")
    private Integer healingDone = 0;

    @Column(name = "crit")
    private Boolean crit = false;

    @Column(name = "hit")
    private Boolean hit = false;

    @Column(name = "raw_roll")
    private Integer rawRoll;

    @Column(name = "total_roll")
    private Integer totalRoll;

    @Column(name = "defender_ac")
    private Integer defenderAc;

    @Column(name = "hp_actor_after")
    private Integer hpActorAfter;

    @Column(name = "hp_target_after")
    private Integer hpTargetAfter;

    @Column(name = "status_effects_applied", columnDefinition = "TEXT")
    private String statusEffectsApplied;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Supported action types in combat. */
    public enum ActionType {
        ATTACK,
        DEFEND,
        SPELL,
        SPECIAL,
        FORFEIT,
        TIMEOUT
    }

    // Constructors
    public BattleTurn() {
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBattleId() {
        return battleId;
    }

    public void setBattleId(String battleId) {
        this.battleId = battleId;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Long getAbilityId() {
        return abilityId;
    }

    public void setAbilityId(Long abilityId) {
        this.abilityId = abilityId;
    }

    public Integer getDamageDealt() {
        return damageDealt;
    }

    public void setDamageDealt(Integer damageDealt) {
        this.damageDealt = damageDealt;
    }

    public Integer getHealingDone() {
        return healingDone;
    }

    public void setHealingDone(Integer healingDone) {
        this.healingDone = healingDone;
    }

    public Boolean getCrit() {
        return crit;
    }

    public void setCrit(Boolean crit) {
        this.crit = crit;
    }

    public Boolean getHit() {
        return hit;
    }

    public void setHit(Boolean hit) {
        this.hit = hit;
    }

    public Integer getRawRoll() {
        return rawRoll;
    }

    public void setRawRoll(Integer rawRoll) {
        this.rawRoll = rawRoll;
    }

    public Integer getTotalRoll() {
        return totalRoll;
    }

    public void setTotalRoll(Integer totalRoll) {
        this.totalRoll = totalRoll;
    }

    public Integer getDefenderAc() {
        return defenderAc;
    }

    public void setDefenderAc(Integer defenderAc) {
        this.defenderAc = defenderAc;
    }

    public Integer getHpActorAfter() {
        return hpActorAfter;
    }

    public void setHpActorAfter(Integer hpActorAfter) {
        this.hpActorAfter = hpActorAfter;
    }

    public Integer getHpTargetAfter() {
        return hpTargetAfter;
    }

    public void setHpTargetAfter(Integer hpTargetAfter) {
        this.hpTargetAfter = hpTargetAfter;
    }

    public String getStatusEffectsApplied() {
        return statusEffectsApplied;
    }

    public void setStatusEffectsApplied(String statusEffectsApplied) {
        this.statusEffectsApplied = statusEffectsApplied;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
