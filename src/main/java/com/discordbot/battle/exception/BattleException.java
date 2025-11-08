package com.discordbot.battle.exception;

/**
 * Base exception for all battle system errors.
 */
public class BattleException extends RuntimeException {
    
    public BattleException(String message) {
        super(message);
    }
    
    public BattleException(String message, Throwable cause) {
        super(message, cause);
    }
}
