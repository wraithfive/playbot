package com.discordbot.battle.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents an active status effect (buff or debuff) on a character during a battle.
 * Status effects can have durations, stack counts, and various magnitudes.
 */
@Entity
@Table(name = "battle_status_effect", indexes = {
    @Index(name = "idx_status_effect_battle", columnList = "battle_id"),
    @Index(name = "idx_status_effect_battle_user", columnList = "battle_id, affected_user_id"),
    @Index(name = "idx_status_effect_type", columnList = "battle_id, affected_user_id, effect_type")
})
public class BattleStatusEffect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the battle this effect belongs to.
     */
    @Column(name = "battle_id", nullable = false)
    private String battleId;

    /**
     * User ID of the character affected by this status effect.
     */
    @Column(name = "affected_user_id", nullable = false)
    private String affectedUserId;

    /**
     * Type of status effect (STUN, BURN, POISON, SHIELD, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "effect_type", nullable = false, length = 50)
    private StatusEffectType effectType;

    /**
     * Duration remaining in turns. 0 means expired/instant.
     * Decremented at the start or end of the affected character's turn.
     */
    @Column(name = "duration_turns", nullable = false)
    private int durationTurns;

    /**
     * Number of stacks for stackable effects (e.g., poison, burn).
     * For non-stackable effects, this is always 1.
     */
    @Column(name = "stacks", nullable = false)
    private int stacks = 1;

    /**
     * Magnitude/power of the effect.
     * - For DoT effects (BURN, POISON, BLEED): damage per turn per stack
     * - For REGEN: healing per turn per stack
     * - For SHIELD: total shield HP
     * - For HASTE/SLOW: bonus/penalty to rolls
     * - For WEAKNESS/STRENGTH: damage modifier percentage
     */
    @Column(name = "magnitude", nullable = false)
    private int magnitude;

    /**
     * User ID of the character who applied this effect (for tracking purposes).
     */
    @Column(name = "source_user_id")
    private String sourceUserId;

    /**
     * Ability key that applied this effect (e.g., "FIREBALL", "POISON_STRIKE").
     */
    @Column(name = "source_ability_key")
    private String sourceAbilityKey;

    /**
     * Turn number when this effect was applied.
     */
    @Column(name = "applied_at_turn", nullable = false)
    private int appliedAtTurn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // --- Constructors ---

    public BattleStatusEffect() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public BattleStatusEffect(String battleId, String affectedUserId, StatusEffectType effectType,
                             int durationTurns, int stacks, int magnitude,
                             String sourceUserId, String sourceAbilityKey, int appliedAtTurn) {
        this();
        this.battleId = battleId;
        this.affectedUserId = affectedUserId;
        this.effectType = effectType;
        this.durationTurns = durationTurns;
        this.stacks = stacks;
        this.magnitude = magnitude;
        this.sourceUserId = sourceUserId;
        this.sourceAbilityKey = sourceAbilityKey;
        this.appliedAtTurn = appliedAtTurn;
    }

    // --- Business Logic ---

    /**
     * Decrease duration by 1 turn. Returns true if effect expired (duration <= 0).
     */
    public boolean tick() {
        this.durationTurns--;
        this.updatedAt = Instant.now();
        return this.durationTurns <= 0;
    }

    /**
     * Add stacks to this effect (for stackable effects).
     * Non-stackable effects cannot be stacked.
     */
    public void addStacks(int additionalStacks) {
        if (!effectType.isStackable()) {
            throw new IllegalStateException("Cannot stack non-stackable effect: " + effectType);
        }
        this.stacks += additionalStacks;
        this.updatedAt = Instant.now();
    }

    /**
     * Refresh the duration of this effect (used when re-applying non-stackable effects).
     */
    public void refreshDuration(int newDuration) {
        this.durationTurns = newDuration;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if this effect is expired (duration <= 0).
     */
    public boolean isExpired() {
        return this.durationTurns <= 0;
    }

    /**
     * Calculate total effect value (magnitude * stacks).
     * Used for DoT, HoT, and other stackable effects.
     */
    public int getTotalMagnitude() {
        return this.magnitude * this.stacks;
    }

    /**
     * Get a display string for this effect (e.g., "🔥 Burning (2 turns, 3 stacks)").
     */
    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(effectType.getEmoji()).append(" ").append(effectType.getDisplayName());

        if (durationTurns > 0) {
            sb.append(" (").append(durationTurns).append(" turn");
            if (durationTurns != 1) sb.append("s");
            if (effectType.isStackable() && stacks > 1) {
                sb.append(", ").append(stacks).append(" stack");
                if (stacks != 1) sb.append("s");
            }
            sb.append(")");
        }

        return sb.toString();
    }

    // --- Lifecycle Hooks ---

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- Getters and Setters ---

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

    public String getAffectedUserId() {
        return affectedUserId;
    }

    public void setAffectedUserId(String affectedUserId) {
        this.affectedUserId = affectedUserId;
    }

    public StatusEffectType getEffectType() {
        return effectType;
    }

    public void setEffectType(StatusEffectType effectType) {
        this.effectType = effectType;
    }

    public int getDurationTurns() {
        return durationTurns;
    }

    public void setDurationTurns(int durationTurns) {
        this.durationTurns = durationTurns;
    }

    public int getStacks() {
        return stacks;
    }

    public void setStacks(int stacks) {
        this.stacks = stacks;
    }

    public int getMagnitude() {
        return magnitude;
    }

    public void setMagnitude(int magnitude) {
        this.magnitude = magnitude;
    }

    public String getSourceUserId() {
        return sourceUserId;
    }

    public void setSourceUserId(String sourceUserId) {
        this.sourceUserId = sourceUserId;
    }

    public String getSourceAbilityKey() {
        return sourceAbilityKey;
    }

    public void setSourceAbilityKey(String sourceAbilityKey) {
        this.sourceAbilityKey = sourceAbilityKey;
    }

    public int getAppliedAtTurn() {
        return appliedAtTurn;
    }

    public void setAppliedAtTurn(int appliedAtTurn) {
        this.appliedAtTurn = appliedAtTurn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
