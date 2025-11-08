package com.discordbot.battle.entity;

import java.util.Set;

/**
 * Constants for character classes and races.
 * Centralizes valid values to avoid magic strings across the codebase.
 */
public final class CharacterConstants {
    
    /**
     * Valid character classes for D&D 5e-inspired battle system.
     */
    public static final Set<String> VALID_CLASSES = Set.of(
        "Warrior",
        "Rogue",
        "Mage",
        "Cleric"
    );
    
    /**
     * Valid character races for D&D 5e-inspired battle system.
     */
    public static final Set<String> VALID_RACES = Set.of(
        "Human",
        "Elf",
        "Dwarf",
        "Halfling"
    );
    
    private CharacterConstants() {
        // Utility class - prevent instantiation
        throw new UnsupportedOperationException("Utility class");
    }
}
