package com.discordbot.battle.exception;

/**
 * Thrown when a character is not found for a user.
 */
public class CharacterNotFoundException extends BattleException {
    
    public CharacterNotFoundException(String guildId, String userId) {
        super(String.format("No character found for user %s in guild %s", userId, guildId));
    }
}
