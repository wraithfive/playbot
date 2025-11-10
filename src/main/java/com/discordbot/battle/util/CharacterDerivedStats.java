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
     * Computes base HP using D&D 5e rules for Level 1 characters.
     * 
     * Formula: hitDieMax + CON modifier
     * - Warrior (Fighter d10): 10 + CON mod
     * - Rogue (d8): 8 + CON mod
     * - Mage (Wizard d6): 6 + CON mod
     * - Cleric (d8): 8 + CON mod
     * 
     * Future leveling (Phase 6): Each level after 1st adds hit die average (or roll) + CON modifier.
     * For example, a level 5 Fighter would have: 10 + (4 × (6 + CON mod)) + CON mod
     * where 6 is the average of d10 (rounded up: (1+10)/2 = 5.5 → 6).
     * 
     * @return Character's max HP, minimum 1
     */
    public static int computeHp(PlayerCharacter pc, BattleProperties battleProperties) {
        int baseByClass = getBaseHpForClass(pc.getCharacterClass(), battleProperties);
        int conMod = abilityMod(pc.getConstitution());
        // D&D 5e Level 1: hitDieMax + CON mod
        return Math.max(1, baseByClass + conMod);
    }

    /**
     * Get base HP for a character class from config (D&D 5e hit die maximum).
     * These values represent the maximum roll of each class's hit die at level 1:
     * - Warrior (Fighter): d10 -> 10
     * - Rogue: d8 -> 8
     * - Mage (Wizard): d6 -> 6
     * - Cleric: d8 -> 8
     */
    public static int getBaseHpForClass(String characterClass, BattleProperties battleProperties) {
        int baseHp = switch (characterClass.toLowerCase()) {
            case "warrior" -> battleProperties.getClassConfig().getWarrior().getBaseHp();
            case "rogue" -> battleProperties.getClassConfig().getRogue().getBaseHp();
            case "mage" -> battleProperties.getClassConfig().getMage().getBaseHp();
            case "cleric" -> battleProperties.getClassConfig().getCleric().getBaseHp();
            default -> 8; // sensible fallback for unknown classes
        };
        return Math.max(1, baseHp); // ensure minimum 1 HP
    }

    /**
     * Computes Armor Class: 10 + DEX modifier (no equipment yet).
     */
    public static int computeAc(PlayerCharacter pc) {
        return 10 + abilityMod(pc.getDexterity());
    }
}
