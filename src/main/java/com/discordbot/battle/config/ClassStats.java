package com.discordbot.battle.config;

/**
 * Configuration for class-specific stats like base HP.
 */
public class ClassStats {
    private int baseHp;

    public ClassStats() {}

    public ClassStats(int baseHp) {
        this.baseHp = baseHp;
    }

    public int getBaseHp() {
        return baseHp;
    }

    public void setBaseHp(int baseHp) {
        this.baseHp = baseHp;
    }
}
