package com.discordbot.battle.util;

import static com.discordbot.battle.entity.PlayerCharacterTestFactory.create;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CharacterDerivedStatsTest {

    /**
     * Creates a properly configured BattleProperties with all required D&D 5e class configs.
     * Required since we removed hardcoded defaults.
     */
    private BattleProperties createConfiguredProperties() {
        BattleProperties props = new BattleProperties();
        // D&D 5e hit die maximums
        props.getClassConfig().getWarrior().setBaseHp(10); // Fighter d10
        props.getClassConfig().getRogue().setBaseHp(8);    // Rogue d8
        props.getClassConfig().getMage().setBaseHp(6);     // Wizard d6
        props.getClassConfig().getCleric().setBaseHp(8);   // Cleric d8
        return props;
    }

    @Test
    void testAbilityMod() {
        assertEquals(-1, CharacterDerivedStats.abilityMod(8));
        assertEquals(0, CharacterDerivedStats.abilityMod(10));
        assertEquals(2, CharacterDerivedStats.abilityMod(14));
        assertEquals(3, CharacterDerivedStats.abilityMod(16));
    }

    @Test
    void testAbilityModEdgeCases() {
        // Very low score
        assertEquals(-4, CharacterDerivedStats.abilityMod(3));
        // Very high score
        assertEquals(5, CharacterDerivedStats.abilityMod(20));
        assertEquals(10, CharacterDerivedStats.abilityMod(30));
        // Boundary: just below typical range
        assertEquals(-2, CharacterDerivedStats.abilityMod(6));
        assertEquals(-2, CharacterDerivedStats.abilityMod(7));
    }

    @Test
    void testComputeAc() {
        PlayerCharacter pc = create("u", "g", "Warrior", "Human",
                10, 14, 10, 10, 10, 10);
        assertEquals(12, CharacterDerivedStats.computeAc(pc)); // 10 + DEX mod(14)=+2
    }

    @Test
    void testComputeHp() {
        BattleProperties props = createConfiguredProperties();
        PlayerCharacter pc = create("u", "g", "Warrior", "Human",
                10, 10, 14, 10, 10, 10);
        // D&D 5e Level 1: Warrior (Fighter d10 max=10) + CON mod(14)=+2 = 12
        assertEquals(10 + CharacterDerivedStats.abilityMod(14), CharacterDerivedStats.computeHp(pc, props));
    }

    @Test
    void testComputeHpForAllClasses() {
        BattleProperties props = createConfiguredProperties();
        int con = 14; // CON mod = +2
        int conMod = CharacterDerivedStats.abilityMod(con);

        // Warrior (Fighter d10): baseHp = 10
        PlayerCharacter warrior = create("u", "g", "Warrior", "Human", 10, 10, con, 10, 10, 10);
        assertEquals(10 + conMod, CharacterDerivedStats.computeHp(warrior, props));

        // Rogue (d8): baseHp = 8
        PlayerCharacter rogue = create("u", "g", "Rogue", "Elf", 10, 10, con, 10, 10, 10);
        assertEquals(8 + conMod, CharacterDerivedStats.computeHp(rogue, props));

        // Mage (Wizard d6): baseHp = 6
        PlayerCharacter mage = create("u", "g", "Mage", "Human", 10, 10, con, 10, 10, 10);
        assertEquals(6 + conMod, CharacterDerivedStats.computeHp(mage, props));

        // Cleric (d8): baseHp = 8
        PlayerCharacter cleric = create("u", "g", "Cleric", "Dwarf", 10, 10, con, 10, 10, 10);
        assertEquals(8 + conMod, CharacterDerivedStats.computeHp(cleric, props));
    }

    @Test
    void testComputeHpBoundaryConditions() {
        BattleProperties props = createConfiguredProperties();

        // Very low CON (8 = -1 modifier)
        PlayerCharacter lowCon = create("u", "g", "Mage", "Human", 10, 10, 8, 10, 10, 10);
        int expectedLowConHp = 6 - 1; // D&D 5e: hitDieMax(6) + CON mod(-1) = 5
        assertEquals(expectedLowConHp, CharacterDerivedStats.computeHp(lowCon, props));

        // Very high CON (20 = +5 modifier)
        PlayerCharacter highCon = create("u", "g", "Warrior", "Human", 10, 10, 20, 10, 10, 10);
        int expectedHighConHp = 10 + 5; // D&D 5e: hitDieMax(10) + CON mod(+5) = 15
        assertEquals(expectedHighConHp, CharacterDerivedStats.computeHp(highCon, props));
    }

    @Test
    void testComputeHpUnknownClassFallback() {
        BattleProperties props = createConfiguredProperties();
        // Unknown class should fall back to baseHp = 8 (like Rogue/Cleric)
        PlayerCharacter unknown = create("u", "g", "Bard", "Human", 10, 10, 12, 10, 10, 10);
        int conMod = CharacterDerivedStats.abilityMod(12); // +1
        assertEquals(8 + conMod, CharacterDerivedStats.computeHp(unknown, props));
    }
}
