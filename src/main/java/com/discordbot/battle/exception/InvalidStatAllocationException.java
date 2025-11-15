package com.discordbot.battle.exception;

/**
 * Thrown when stat allocation violates point-buy rules.
 */
public class InvalidStatAllocationException extends BattleException {
    
    public InvalidStatAllocationException(String message) {
        super(message);
    }
}
