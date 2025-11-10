package com.discordbot.battle.effect;

/**
 * Centralized registry of effect stat keys used in ability effects.
 * Prevents typos and provides type-safe access to stat names.
 * 
 * Used in effect strings like "DAMAGE+3,AC+1,MAX_HP+5" and parsed by EffectParser.
 */
public enum EffectStat {
    // Combat Stats
    DAMAGE("DAMAGE"),
    AC("AC"),
    MAX_HP("MAX_HP"),
    SPELL_DAMAGE("SPELL_DAMAGE"),
    HEAL_BONUS("HEAL_BONUS"),
    CRIT_DAMAGE("CRIT_DAMAGE"),
    
    // Ability Scores
    STR("STR"),
    DEX("DEX"),
    CON("CON"),
    INT("INT"),
    WIS("WIS"),
    CHA("CHA");
    
    private final String key;
    
    EffectStat(String key) {
        this.key = key;
    }
    
    /**
     * Get the string key used in effect strings.
     * @return The effect stat key (e.g., "DAMAGE", "AC")
     */
    public String getKey() {
        return key;
    }
    
    @Override
    public String toString() {
        return key;
    }
    
    /**
     * Parse a string key into an EffectStat enum (case-insensitive).
     * Returns null if not found.
     */
    public static EffectStat fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (EffectStat stat : values()) {
            if (stat.key.equalsIgnoreCase(key)) {
                return stat;
            }
        }
        return null;
    }
}
