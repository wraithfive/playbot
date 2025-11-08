package com.discordbot.battle.exception;

/**
 * Thrown when battle commands are invoked while battle.enabled=false.
 */
public class BattleNotEnabledException extends BattleException {
    
    public BattleNotEnabledException() {
        super("Battle system is currently disabled.");
    }
}
