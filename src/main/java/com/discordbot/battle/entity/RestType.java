package com.discordbot.battle.entity;

/**
 * Defines the type of rest required to restore ability charges or spell slots.
 * Follows D&D 5e rest mechanics.
 */
public enum RestType {
    /**
     * No rest required - ability is always available (subject to other restrictions like cooldowns).
     */
    NONE,
    
    /**
     * Short rest (1 hour) - restores some resources.
     * In D&D 5e, classes like Warlock recover spell slots on short rest.
     */
    SHORT_REST,
    
    /**
     * Long rest (8 hours) - restores all resources.
     * In D&D 5e, most classes recover all spell slots and hit points on long rest.
     */
    LONG_REST
}
