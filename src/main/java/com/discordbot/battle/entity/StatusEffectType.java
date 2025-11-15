package com.discordbot.battle.entity;

/**
 * Types of status effects that can be applied to characters during battle.
 * Each effect type has specific behavior defined in StatusEffectService.
 */
public enum StatusEffectType {
    /**
     * STUN: Target cannot act on their next turn.
     * Duration: 1 turn typically
     * Stackable: No
     */
    STUN,

    /**
     * BURN: Target takes damage at the start of their turn.
     * Duration: Multiple turns
     * Stackable: Yes (increased damage per stack)
     */
    BURN,

    /**
     * POISON: Target takes damage at the start of their turn.
     * Duration: Multiple turns
     * Stackable: Yes (increased damage per stack)
     */
    POISON,

    /**
     * REGENERATION: Target heals at the start of their turn.
     * Duration: Multiple turns
     * Stackable: Yes (increased healing per stack)
     */
    REGEN,

    /**
     * SHIELD: Target has temporary bonus HP that absorbs damage.
     * Duration: Until depleted or expires
     * Stackable: Yes (increases shield amount)
     */
    SHIELD,

    /**
     * HASTE: Target gains bonus to attack rolls and AC.
     * Duration: Multiple turns
     * Stackable: No (but can refresh)
     */
    HASTE,

    /**
     * SLOW: Target has penalty to attack rolls and AC.
     * Duration: Multiple turns
     * Stackable: No (but can refresh)
     */
    SLOW,

    /**
     * BLEED: Target takes damage at the end of their turn.
     * Duration: Multiple turns
     * Stackable: Yes (increased damage per stack)
     */
    BLEED,

    /**
     * WEAKNESS: Target deals reduced damage.
     * Duration: Multiple turns
     * Stackable: No
     */
    WEAKNESS,

    /**
     * STRENGTH: Target deals increased damage.
     * Duration: Multiple turns
     * Stackable: No
     */
    STRENGTH,

    /**
     * PROTECTION: Target takes reduced damage.
     * Duration: Multiple turns
     * Stackable: No
     */
    PROTECTION,

    /**
     * VULNERABILITY: Target takes increased damage.
     * Duration: Multiple turns
     * Stackable: No
     */
    VULNERABILITY;

    /**
     * Check if this effect type is stackable.
     */
    public boolean isStackable() {
        return switch (this) {
            case BURN, POISON, REGEN, SHIELD, BLEED -> true;
            case STUN, HASTE, SLOW, WEAKNESS, STRENGTH, PROTECTION, VULNERABILITY -> false;
        };
    }

    /**
     * Get display emoji for this effect type.
     */
    public String getEmoji() {
        return switch (this) {
            case STUN -> "😵";
            case BURN -> "🔥";
            case POISON -> "☠️";
            case REGEN -> "💚";
            case SHIELD -> "🛡️";
            case HASTE -> "⚡";
            case SLOW -> "🐌";
            case BLEED -> "🩸";
            case WEAKNESS -> "💔";
            case STRENGTH -> "💪";
            case PROTECTION -> "🛡️";
            case VULNERABILITY -> "⚠️";
        };
    }

    /**
     * Get display name for this effect type.
     */
    public String getDisplayName() {
        return switch (this) {
            case STUN -> "Stunned";
            case BURN -> "Burning";
            case POISON -> "Poisoned";
            case REGEN -> "Regenerating";
            case SHIELD -> "Shielded";
            case HASTE -> "Hasted";
            case SLOW -> "Slowed";
            case BLEED -> "Bleeding";
            case WEAKNESS -> "Weakened";
            case STRENGTH -> "Strengthened";
            case PROTECTION -> "Protected";
            case VULNERABILITY -> "Vulnerable";
        };
    }
}
