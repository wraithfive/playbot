package com.discordbot.battle.effect;

import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.entity.CharacterAbility;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CharacterStatsCalculatorTest {

    private CharacterAbility abilityWithEffect(String effect) {
        CharacterAbility ca = mock(CharacterAbility.class, RETURNS_DEEP_STUBS);
        when(ca.getAbility().getEffect()).thenReturn(effect);
        return ca;
    }

    @Test
    void testCalculateStats_Mage_AbilityBonusesApplied() {
        // Mage with INT 15 (+2), WIS 10 (+0), STR 12 (+1), DEX 14 (+2), CON 12 (+1)
        PlayerCharacter pc = new PlayerCharacter(
                "123456789012345601", "987654321098765432", "Mage", "Human",
                12, 14, 12, 15, 10, 10
        );

        List<CharacterAbility> learned = List.of(
                abilityWithEffect("DAMAGE+2,AC+1,MAX_HP+3,SPELL_DAMAGE+4,HEAL_BONUS+2,CRIT_DAMAGE+20")
        );

        // Base HP for Mage (from config) is 6 (Wizard d6 hit die max); pass as parameter here
        int baseHp = 6;

        CharacterStatsCalculator.CombatStats stats = CharacterStatsCalculator.calculateStats(pc, learned, baseHp);

        // HP = hitDieMax(6) + CON mod(+1) + MAX_HP(+3) = 10
        assertEquals(10, stats.maxHp());
        // AC = 10 + DEX mod(+2) + AC(+1) = 13
        assertEquals(13, stats.armorClass());
        // Attack damage bonus = STR mod(+1) + DAMAGE(+2) = 3
        assertEquals(3, stats.attackDamageBonus());
        // Spell damage bonus = INT mod(+2) + SPELL_DAMAGE(+4) = 6
        assertEquals(6, stats.spellDamageBonus());
        // Healing bonus = WIS mod(+0) + HEAL_BONUS(+2) = 2
        assertEquals(2, stats.healingBonus());
        // Crit damage bonus = +20%
        assertEquals(20, stats.critDamageBonus());

        assertTrue(stats.combinedEffect().hasTag("CRIT_DAMAGE") || stats.combinedEffect().getTotalBonus("CRIT_DAMAGE") == 20);
    }

    @Test
    void testCalculateStats_Cleric_UsesWisdomForSpells() {
        // Cleric with INT 8 (-1), WIS 16 (+3)
        PlayerCharacter pc = new PlayerCharacter(
                "123456789012345602", "987654321098765432", "Cleric", "Dwarf",
                10, 10, 10, 8, 16, 10
        );

        List<CharacterAbility> learned = List.of(
                abilityWithEffect("SPELL_DAMAGE+2,HEAL_BONUS+1")
        );

        int baseHp = 8; // Cleric d8 hit die max

        CharacterStatsCalculator.CombatStats stats = CharacterStatsCalculator.calculateStats(pc, learned, baseHp);

        // Spell damage bonus uses WIS mod(+3) + 2 = 5
        assertEquals(5, stats.spellDamageBonus());
        // Healing bonus = WIS mod(+3) + 1 = 4
        assertEquals(4, stats.healingBonus());
    }

    @Test
    void testCalculateStats_Warrior_BasicStats() {
        // Warrior with STR 16 (+3), DEX 14 (+2), CON 14 (+2)
        PlayerCharacter pc = new PlayerCharacter(
                "123456789012345603", "987654321098765432", "Warrior", "Human",
                16, 14, 14, 10, 10, 10
        );

        List<CharacterAbility> learned = List.of(
                abilityWithEffect("DAMAGE+3,MAX_HP+5")
        );

        int baseHp = 10; // Fighter d10 hit die max

        CharacterStatsCalculator.CombatStats stats = CharacterStatsCalculator.calculateStats(pc, learned, baseHp);

        // HP = 10 + CON mod(+2) + MAX_HP(+5) = 17
        assertEquals(17, stats.maxHp());
        // AC = 10 + DEX mod(+2) = 12 (no AC bonus from abilities)
        assertEquals(12, stats.armorClass());
        // Attack damage bonus = STR mod(+3) + DAMAGE(+3) = 6
        assertEquals(6, stats.attackDamageBonus());
        // Warrior uses best of INT/WIS for spells (both +0) = 0
        assertEquals(0, stats.spellDamageBonus());
    }

    @Test
    void testCalculateStats_Rogue_BasicStats() {
        // Rogue with DEX 18 (+4), STR 10 (+0), CON 12 (+1)
        PlayerCharacter pc = new PlayerCharacter(
                "123456789012345604", "987654321098765432", "Rogue", "Halfling",
                10, 18, 12, 12, 10, 14
        );

        List<CharacterAbility> learned = List.of(
                abilityWithEffect("AC+2,DAMAGE+1")
        );

        int baseHp = 8; // Rogue d8 hit die max

        CharacterStatsCalculator.CombatStats stats = CharacterStatsCalculator.calculateStats(pc, learned, baseHp);

        // HP = 8 + CON mod(+1) = 9
        assertEquals(9, stats.maxHp());
        // AC = 10 + DEX mod(+4) + AC(+2) = 16
        assertEquals(16, stats.armorClass());
        // Attack damage bonus = STR mod(+0) + DAMAGE(+1) = 1
        assertEquals(1, stats.attackDamageBonus());
    }

    @Test
    void testCalculateStats_EmptyAbilityList() {
        // Character with no learned abilities
        PlayerCharacter pc = new PlayerCharacter(
                "123456789012345605", "987654321098765432", "Mage", "Elf",
                10, 12, 10, 14, 10, 10
        );

        List<CharacterAbility> learned = List.of(); // Empty list

        int baseHp = 6;

        CharacterStatsCalculator.CombatStats stats = CharacterStatsCalculator.calculateStats(pc, learned, baseHp);

        // HP = 6 + CON mod(+0) = 6
        assertEquals(6, stats.maxHp());
        // AC = 10 + DEX mod(+1) = 11
        assertEquals(11, stats.armorClass());
        // Attack damage = STR mod(+0) = 0
        assertEquals(0, stats.attackDamageBonus());
        // Spell damage = INT mod(+2) = 2
        assertEquals(2, stats.spellDamageBonus());
    }

    @Test
    void testCalculateStats_NegativeModifiers() {
        // Character with penalties: AC-1, DAMAGE-2
        PlayerCharacter pc = new PlayerCharacter(
                "123456789012345606", "987654321098765432", "Warrior", "Human",
                12, 14, 12, 10, 10, 10
        );

        List<CharacterAbility> learned = List.of(
                abilityWithEffect("AC-1,DAMAGE-2")
        );

        int baseHp = 10;

        CharacterStatsCalculator.CombatStats stats = CharacterStatsCalculator.calculateStats(pc, learned, baseHp);

        // AC = 10 + DEX mod(+2) + AC(-1) = 11
        assertEquals(11, stats.armorClass());
        // Attack damage = STR mod(+1) + DAMAGE(-2) = -1
        assertEquals(-1, stats.attackDamageBonus());
    }

    @Test
    void testCalculateStats_MinimumHpConstraint() {
        // Character with very low CON and negative MAX_HP modifier
        PlayerCharacter pc = new PlayerCharacter(
                "123456789012345607", "987654321098765432", "Mage", "Human",
                10, 10, 6, 10, 10, 10 // CON 6 = -2 modifier
        );

        List<CharacterAbility> learned = List.of(
                abilityWithEffect("MAX_HP-10") // Large HP penalty
        );

        int baseHp = 6;

        CharacterStatsCalculator.CombatStats stats = CharacterStatsCalculator.calculateStats(pc, learned, baseHp);

        // HP = 6 + CON mod(-2) + MAX_HP(-10) = -6, but minimum is 1
        assertEquals(1, stats.maxHp());
    }

    @Test
    void testCalculateStats_AbilityScoreBonuses() {
        // Test that ability score bonuses (STR+2, DEX+1, CON+2) are correctly applied
        PlayerCharacter pc = new PlayerCharacter(
                "123456789012345608", "987654321098765432", "Warrior", "Human",
                10, 10, 10, 10, 10, 10 // All 10s (+0 modifiers)
        );

        List<CharacterAbility> learned = List.of(
                abilityWithEffect("STR+2,DEX+1,CON+2") // Boost ability scores
        );

        int baseHp = 10;

        CharacterStatsCalculator.CombatStats stats = CharacterStatsCalculator.calculateStats(pc, learned, baseHp);

        // STR becomes 12 (+1 mod), DEX becomes 11 (+0 mod), CON becomes 12 (+1 mod)
        // HP = 10 + CON mod(+1) = 11
        assertEquals(11, stats.maxHp());
        // AC = 10 + DEX mod(+0) = 10
        assertEquals(10, stats.armorClass());
        // Attack damage = STR mod(+1) = 1
        assertEquals(1, stats.attackDamageBonus());
    }
}
