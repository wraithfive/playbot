package com.discordbot.battle.util;

/** Centralized constants and helpers for battle-related component IDs. */
public final class BattleComponentIds {
    private BattleComponentIds() {}
    public static final String PREFIX = "battle:";
    public static String componentId(String battleId, String action) {
        return PREFIX + battleId + ":" + action;
    }
    public static boolean isBattleComponent(String componentId) {
        return componentId != null && componentId.startsWith(PREFIX);
    }
}
