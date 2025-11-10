package com.discordbot.battle.util;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;

/**
 * Utility methods for computing derived character statistics (HP, AC, modifiers).
 * Keeps math centralized so handlers stay lean.
 */
public final class CharacterDerivedStats {

    private CharacterDerivedStats() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * D&D 5e style ability modifier: floor((score - 10) / 2).
     */
    public static int abilityMod(int score) {
        return (int) Math.floor((score - 10) / 2.0);
    }

    /**
     * Computes base HP: class base HP + 10 + (CON modifier * level).
     * NOTE: Level not yet persisted; assume level 1 for now.
     */
    public static int computeHp(PlayerCharacter pc, BattleProperties battleProperties) {
        int baseByClass = switch (pc.getCharacterClass().toLowerCase()) {
            case "warrior" -> battleProperties.getClassConfig().getWarrior().getBaseHp();
            case "rogue" -> battleProperties.getClassConfig().getRogue().getBaseHp();
            case "mage" -> battleProperties.getClassConfig().getMage().getBaseHp();
            case "cleric" -> battleProperties.getClassConfig().getCleric().getBaseHp();
            default -> 8; // sensible fallback
        };
        int conMod = abilityMod(pc.getConstitution());
        int level = 1; // TODO: replace with pc.getLevel() when level field is added
        return baseByClass + 10 + (conMod * level);
    }

    /**
     * Computes Armor Class: 10 + DEX modifier (no equipment yet).
     */
    public static int computeAc(PlayerCharacter pc) {
        return 10 + abilityMod(pc.getDexterity());
    }
}
