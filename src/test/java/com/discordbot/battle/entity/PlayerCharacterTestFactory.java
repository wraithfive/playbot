package com.discordbot.battle.entity;

import java.lang.reflect.Method;

/**
 * Test factory for creating PlayerCharacter instances without Discord snowflake validation.
 * This allows tests to use simple IDs like "user123" instead of valid 18-digit Discord snowflakes.
 */
public class PlayerCharacterTestFactory {

    /**
     * Creates a PlayerCharacter for testing without validating Discord snowflake format.
     * Use this in tests where you need simple IDs like "user123" or "guild456".
     *
     * @param userId Test user ID (doesn't need to be a valid Discord snowflake)
     * @param guildId Test guild ID (doesn't need to be a valid Discord snowflake)
     * @param characterClass Character class (e.g., "Warrior", "Mage", "Rogue", "Cleric")
     * @param race Character race (e.g., "Human", "Elf", "Dwarf", "Orc")
     * @param strength Strength score (typically 8-15)
     * @param dexterity Dexterity score (typically 8-15)
     * @param constitution Constitution score (typically 8-15)
     * @param intelligence Intelligence score (typically 8-15)
     * @param wisdom Wisdom score (typically 8-15)
     * @param charisma Charisma score (typically 8-15)
     * @return A PlayerCharacter instance for testing
     */
    public static PlayerCharacter create(String userId, String guildId,
                                        String characterClass, String race,
                                        int strength, int dexterity, int constitution,
                                        int intelligence, int wisdom, int charisma) {
        // Use package-private constructor with validateIds=false
        PlayerCharacter character = new PlayerCharacter(userId, guildId, characterClass, race,
                                                        strength, dexterity, constitution,
                                                        intelligence, wisdom, charisma,
                                                        false); // Skip Discord snowflake validation for tests

        // Initialize timestamp fields that would normally be set by @PrePersist
        // Call the protected onCreate() method using reflection
        try {
            Method onCreateMethod = PlayerCharacter.class.getDeclaredMethod("onCreate");
            onCreateMethod.setAccessible(true);
            onCreateMethod.invoke(character);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize PlayerCharacter timestamps for testing", e);
        }

        return character;
    }
}
