package com.discordbot.battle.effect;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed representation of an ability's effects.
 * Effects are stored in the database as strings like "DAMAGE+3,FIRE" or "MAX_HP+5,HEAL_BONUS+2".
 * This class provides a structured way to work with those effects.
 */
public class AbilityEffect {

    private final List<Modifier> modifiers;
    private final List<String> tags;

    public AbilityEffect(List<Modifier> modifiers, List<String> tags) {
        this.modifiers = modifiers != null ? modifiers : new ArrayList<>();
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    /**
     * Get all numeric modifiers (e.g., DAMAGE+3, MAX_HP+5).
     */
    public List<Modifier> getModifiers() {
        return modifiers;
    }

    /**
     * Get all tags (e.g., FIRE, RADIANT, BONUS_ACTION).
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * Get total bonus for a specific stat (e.g., sum all DAMAGE modifiers).
     */
    public int getTotalBonus(String stat) {
        return modifiers.stream()
            .filter(m -> m.stat().equalsIgnoreCase(stat))
            .mapToInt(Modifier::value)
            .sum();
    }

    /**
     * Type-safe overload to get total bonus using EffectStat enum.
     * Prevents typos and provides compile-time safety.
     */
    public int getTotalBonus(EffectStat stat) {
        return getTotalBonus(stat.getKey());
    }

    /**
     * Check if this effect has a specific tag.
     */
    public boolean hasTag(String tag) {
        return tags.stream().anyMatch(t -> t.equalsIgnoreCase(tag));
    }

    /**
     * Represents a single numeric modifier like "DAMAGE+3" or "AC+2".
     */
    public record Modifier(String stat, int value) {}

    @Override
    public String toString() {
        return "AbilityEffect{modifiers=" + modifiers + ", tags=" + tags + "}";
    }
}
