package com.discordbot.battle.effect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EffectParserTest {

    @Test
    void testParseSimpleModifier() {
        AbilityEffect effect = EffectParser.parse("DAMAGE+3");

        assertEquals(1, effect.getModifiers().size());
        assertEquals(0, effect.getTags().size());

        AbilityEffect.Modifier mod = effect.getModifiers().get(0);
        assertEquals("DAMAGE", mod.stat());
        assertEquals(3, mod.value());
    }

    @Test
    void testParseNegativeModifier() {
        AbilityEffect effect = EffectParser.parse("AC-2");

        assertEquals(1, effect.getModifiers().size());
        AbilityEffect.Modifier mod = effect.getModifiers().get(0);
        assertEquals("AC", mod.stat());
        assertEquals(-2, mod.value());
    }

    @Test
    void testParseModifierWithTag() {
        AbilityEffect effect = EffectParser.parse("DAMAGE+3,FIRE");

        assertEquals(1, effect.getModifiers().size());
        assertEquals(1, effect.getTags().size());

        assertEquals("DAMAGE", effect.getModifiers().get(0).stat());
        assertEquals(3, effect.getModifiers().get(0).value());
        assertEquals("FIRE", effect.getTags().get(0));
    }

    @Test
    void testParseMultipleModifiers() {
        AbilityEffect effect = EffectParser.parse("MAX_HP+5,HEAL_BONUS+2");

        assertEquals(2, effect.getModifiers().size());
        assertEquals(0, effect.getTags().size());

        assertEquals(5, effect.getTotalBonus("MAX_HP"));
        assertEquals(2, effect.getTotalBonus("HEAL_BONUS"));
    }

    @Test
    void testParseComplexEffect() {
        AbilityEffect effect = EffectParser.parse("DAMAGE+4,RADIANT,NEXT_ATTACK_ADVANTAGE");

        assertEquals(1, effect.getModifiers().size());
        assertEquals(2, effect.getTags().size());

        assertEquals(4, effect.getTotalBonus("DAMAGE"));
        assertTrue(effect.hasTag("RADIANT"));
        assertTrue(effect.hasTag("NEXT_ATTACK_ADVANTAGE"));
    }

    @Test
    void testParseEmptyString() {
        AbilityEffect effect = EffectParser.parse("");

        assertEquals(0, effect.getModifiers().size());
        assertEquals(0, effect.getTags().size());
    }

    @Test
    void testParseNull() {
        AbilityEffect effect = EffectParser.parse(null);

        assertEquals(0, effect.getModifiers().size());
        assertEquals(0, effect.getTags().size());
    }

    @Test
    void testParseWithWhitespace() {
        AbilityEffect effect = EffectParser.parse(" DAMAGE+3 , FIRE , AC+1 ");

        assertEquals(2, effect.getModifiers().size());
        assertEquals(1, effect.getTags().size());

        assertEquals(3, effect.getTotalBonus("DAMAGE"));
        assertEquals(1, effect.getTotalBonus("AC"));
        assertTrue(effect.hasTag("FIRE"));
    }

    @Test
    void testParseOnlyTag() {
        AbilityEffect effect = EffectParser.parse("RADIANT");

        assertEquals(0, effect.getModifiers().size());
        assertEquals(1, effect.getTags().size());
        assertEquals("RADIANT", effect.getTags().get(0));
    }

    @Test
    void testGetTotalBonusSumsMultiple() {
        AbilityEffect effect = EffectParser.parse("DAMAGE+3,FIRE,DAMAGE+2");

        // Should sum multiple DAMAGE modifiers
        assertEquals(5, effect.getTotalBonus("DAMAGE"));
    }

    @Test
    void testGetTotalBonusNonExistent() {
        AbilityEffect effect = EffectParser.parse("DAMAGE+3");

        // Should return 0 for stats that don't exist
        assertEquals(0, effect.getTotalBonus("AC"));
    }

    @Test
    void testParseAndCombine() {
        List<String> effects = List.of(
            "DAMAGE+3,FIRE",
            "MAX_HP+5",
            "DAMAGE+2,AC+1"
        );

        AbilityEffect combined = EffectParser.parseAndCombine(effects);

        assertEquals(5, combined.getTotalBonus("DAMAGE")); // 3 + 2
        assertEquals(5, combined.getTotalBonus("MAX_HP"));
        assertEquals(1, combined.getTotalBonus("AC"));
        assertTrue(combined.hasTag("FIRE"));
    }

    @Test
    void testHasTagCaseInsensitive() {
        AbilityEffect effect = EffectParser.parse("RADIANT");

        assertTrue(effect.hasTag("RADIANT"));
        assertTrue(effect.hasTag("radiant"));
        assertTrue(effect.hasTag("Radiant"));
    }

    @Test
    void testGetTotalBonusCaseInsensitive() {
        AbilityEffect effect = EffectParser.parse("DAMAGE+3");

        assertEquals(3, effect.getTotalBonus("DAMAGE"));
        assertEquals(3, effect.getTotalBonus("damage"));
        assertEquals(3, effect.getTotalBonus("Damage"));
    }

    @Test
    void testNegativeModifiersStack() {
        // Test that multiple negative modifiers stack correctly
        AbilityEffect effect = EffectParser.parse("AC-2,AC-1,DAMAGE-3");

        assertEquals(-3, effect.getTotalBonus("AC")); // -2 + -1
        assertEquals(-3, effect.getTotalBonus("DAMAGE"));
    }

    @Test
    void testMixedPositiveNegativeModifiers() {
        // Test that positive and negative modifiers combine correctly
        AbilityEffect effect = EffectParser.parse("DAMAGE+5,DAMAGE-2,AC+3,AC-1");

        assertEquals(3, effect.getTotalBonus("DAMAGE")); // +5 - 2
        assertEquals(2, effect.getTotalBonus("AC")); // +3 - 1
    }

    @Test
    void testLargeModifierValues() {
        // Test that large modifier values are handled correctly
        AbilityEffect effect = EffectParser.parse("MAX_HP+100,CRIT_DAMAGE+50");

        assertEquals(100, effect.getTotalBonus("MAX_HP"));
        assertEquals(50, effect.getTotalBonus("CRIT_DAMAGE"));
    }

    @Test
    void testZeroValueModifier() {
        // Edge case: +0 modifier (should still be parsed)
        AbilityEffect effect = EffectParser.parse("DAMAGE+0");

        assertEquals(1, effect.getModifiers().size());
        assertEquals(0, effect.getTotalBonus("DAMAGE"));
    }

    @Test
    void testMultipleTagsOnly() {
        // Test parsing multiple tags without any numeric modifiers
        AbilityEffect effect = EffectParser.parse("FIRE,RADIANT,BONUS_ACTION,AOE");

        assertEquals(0, effect.getModifiers().size());
        assertEquals(4, effect.getTags().size());
        assertTrue(effect.hasTag("FIRE"));
        assertTrue(effect.hasTag("RADIANT"));
        assertTrue(effect.hasTag("BONUS_ACTION"));
        assertTrue(effect.hasTag("AOE"));
    }

    @Test
    void testComplexStackedModifiers() {
        // Test complex stacking with same stat appearing multiple times
        List<String> effects = List.of(
            "DAMAGE+2,FIRE",
            "DAMAGE+3,AC+1",
            "DAMAGE-1,AC+2",
            "MAX_HP+10"
        );

        AbilityEffect combined = EffectParser.parseAndCombine(effects);

        assertEquals(4, combined.getTotalBonus("DAMAGE")); // 2 + 3 - 1
        assertEquals(3, combined.getTotalBonus("AC")); // 1 + 2
        assertEquals(10, combined.getTotalBonus("MAX_HP"));
        assertTrue(combined.hasTag("FIRE"));
    }
}
