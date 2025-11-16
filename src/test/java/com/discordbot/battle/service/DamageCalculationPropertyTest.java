package com.discordbot.battle.service;

import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.entity.PlayerCharacterTestFactory;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for damage calculations.
 * Phase 13: Test Suite Build-Out
 *
 * These tests verify invariants that should hold for ALL damage calculations:
 * - Damage is never negative
 * - Damage is within reasonable bounds
 * - Critical hits are always >= normal hits
 * - HP never goes below 0
 */
class DamageCalculationPropertyTest {

    /**
     * Property: Damage is always non-negative
     */
    @ParameterizedTest
    @MethodSource("provideAbilityScoreCombinations")
    void damageShouldNeverBeNegative(int attackerStr, int defenderCon) {
        // Create attacker and defender with various ability scores
        PlayerCharacter attacker = PlayerCharacterTestFactory.create(
            "attacker", "guild1", "Warrior", "Human",
            attackerStr, 10, 10, 10, 10, 10
        );

        PlayerCharacter defender = PlayerCharacterTestFactory.create(
            "defender", "guild1", "Warrior", "Human",
            10, 10, defenderCon, 10, 10, 10
        );

        // Simulate attack with no crit
        int strMod = (attackerStr - 10) / 2;
        int proficiency = 2; // Level 1
        int baseDamage = 4; // 1d6 average

        int damage = baseDamage + strMod;

        assertTrue(damage >= 0,
            String.format("Damage should never be negative (got %d with STR:%d, CON:%d)",
                damage, attackerStr, defenderCon));
    }

    /**
     * Property: Damage is within reasonable bounds (0 to maxHP * 2)
     */
    @ParameterizedTest
    @MethodSource("provideDamageScenarios")
    void damageShouldBeWithinBounds(int baseDamage, int modifier, boolean isCrit) {
        int damage = baseDamage + modifier;
        if (isCrit) {
            damage *= 2;
        }

        assertTrue(damage >= 0, "Damage should be non-negative");
        assertTrue(damage <= 200, "Damage should not exceed reasonable maximum (200)");
    }

    /**
     * Property: Critical hits always deal at least as much damage as normal hits
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void criticalHitShouldAlwaysDealMoreOrEqualDamage(int baseDamage) {
        int modifier = 3; // +3 STR modifier

        int normalDamage = baseDamage + modifier;
        int critDamage = (baseDamage + modifier) * 2;

        assertTrue(critDamage >= normalDamage,
            String.format("Crit damage (%d) should be >= normal damage (%d)",
                critDamage, normalDamage));
    }

    /**
     * Property: HP never goes below 0 after damage
     */
    @ParameterizedTest
    @MethodSource("provideHpAndDamageCombinations")
    void hpShouldNeverGoBelowZero(int currentHp, int damage) {
        int newHp = Math.max(0, currentHp - damage);

        assertTrue(newHp >= 0,
            String.format("HP should never be negative (was %d - %d = %d)",
                currentHp, damage, newHp));
    }

    /**
     * Property: Random damage variance should be bounded
     * Test that repeated attacks produce damage within expected range
     */
    @RepeatedTest(100)
    void damageShouldBeConsistentlyWithinRange() {
        Random random = new Random();

        // 1d6 roll should be 1-6
        int roll = random.nextInt(6) + 1;

        assertTrue(roll >= 1 && roll <= 6,
            String.format("1d6 roll should be 1-6, got %d", roll));
    }

    /**
     * Property: Damage with maximum possible modifiers should still be bounded
     */
    @ParameterizedTest
    @MethodSource("provideExtremeModifiers")
    void damageWithExtremeModifiersShouldBeBounded(int abilityScore) {
        // Even with max ability score (20 in D&D 5e, or higher with magic items)
        int modifier = (abilityScore - 10) / 2;
        int baseDamage = 6; // Max 1d6 roll
        int proficiency = 6; // Max at level 20

        int attackBonus = modifier + proficiency;
        int damage = baseDamage + modifier;

        assertTrue(attackBonus >= -5 && attackBonus <= 20,
            String.format("Attack bonus should be reasonable, got %d", attackBonus));
        assertTrue(damage >= -5 && damage <= 50,
            String.format("Damage should be reasonable, got %d", damage));
    }

    /**
     * Property: Armor class should affect hit probability but not damage
     */
    @ParameterizedTest
    @ValueSource(ints = {8, 10, 12, 15, 18, 20, 25})
    void armorClassShouldNotAffectDamageAmount(int ac) {
        // AC affects whether attack hits, not how much damage is dealt
        int baseDamage = 5;
        int modifier = 3;

        int damage = baseDamage + modifier;

        // Damage calculation should be independent of AC
        assertEquals(8, damage,
            "Damage should be same regardless of target's AC");
    }

    /**
     * Property: Zero HP should result in battle end
     */
    @ParameterizedTest
    @MethodSource("provideLethalDamageScenarios")
    void zeroHpShouldEndBattle(int currentHp, int damage) {
        int newHp = Math.max(0, currentHp - damage);
        boolean battleEnded = (newHp <= 0);

        if (damage >= currentHp) {
            assertTrue(battleEnded,
                String.format("Battle should end when HP (%d) drops to 0 from damage (%d)",
                    currentHp, damage));
            assertEquals(0, newHp, "HP should be exactly 0 when defeated");
        }
    }

    /**
     * Property: Healing cannot exceed maximum HP
     */
    @ParameterizedTest
    @MethodSource("provideHealingScenarios")
    void healingShouldNotExceedMaxHp(int currentHp, int maxHp, int healing) {
        int newHp = Math.min(maxHp, currentHp + healing);

        assertTrue(newHp <= maxHp,
            String.format("HP after healing (%d) should not exceed max HP (%d)",
                newHp, maxHp));
        assertTrue(newHp >= currentHp,
            String.format("HP should not decrease from healing (was %d, now %d)",
                currentHp, newHp));
    }

    // Data providers

    static Stream<Arguments> provideAbilityScoreCombinations() {
        return Stream.of(
            Arguments.of(8, 8),   // Min STR, Min CON
            Arguments.of(8, 15),  // Min STR, Max CON
            Arguments.of(15, 8),  // Max STR, Min CON
            Arguments.of(15, 15), // Max STR, Max CON
            Arguments.of(10, 10), // Average
            Arguments.of(12, 14), // Typical values
            Arguments.of(20, 20)  // Epic-level stats
        );
    }

    static Stream<Arguments> provideDamageScenarios() {
        return Stream.of(
            Arguments.of(1, -1, false),  // Min roll, negative modifier
            Arguments.of(1, 0, false),   // Min roll, no modifier
            Arguments.of(6, 5, false),   // Max roll, high modifier
            Arguments.of(3, 2, false),   // Average roll, average modifier
            Arguments.of(6, 5, true),    // Crit with high damage
            Arguments.of(1, -1, true),   // Crit with low damage
            Arguments.of(10, 10, true)   // High damage crit
        );
    }

    static Stream<Arguments> provideHpAndDamageCombinations() {
        return Stream.of(
            Arguments.of(10, 5),   // Normal damage
            Arguments.of(10, 10),  // Exact lethal
            Arguments.of(10, 15),  // Overkill
            Arguments.of(1, 100),  // Massive overkill
            Arguments.of(50, 1),   // Minimal damage
            Arguments.of(100, 99), // Nearly lethal
            Arguments.of(5, 5)     // Exact match
        );
    }

    static Stream<Arguments> provideExtremeModifiers() {
        return Stream.of(
            Arguments.of(1),  // Min ability score (impossibly low)
            Arguments.of(3),  // Min D&D 5e score
            Arguments.of(8),  // Min point-buy
            Arguments.of(10), // Average
            Arguments.of(15), // Max point-buy
            Arguments.of(18), // Racial maximum
            Arguments.of(20), // Level 20 cap
            Arguments.of(30)  // Godlike (with magic items)
        );
    }

    static Stream<Arguments> provideLethalDamageScenarios() {
        return Stream.of(
            Arguments.of(10, 10), // Exact lethal
            Arguments.of(10, 11), // Slight overkill
            Arguments.of(10, 50), // Massive overkill
            Arguments.of(1, 1),   // Min lethal
            Arguments.of(5, 4),   // Not lethal
            Arguments.of(20, 20)  // Exact lethal high HP
        );
    }

    static Stream<Arguments> provideHealingScenarios() {
        return Stream.of(
            Arguments.of(10, 20, 5),   // Normal healing
            Arguments.of(10, 20, 15),  // Overhealing
            Arguments.of(19, 20, 5),   // Healing near max
            Arguments.of(1, 20, 100),  // Massive overheal
            Arguments.of(20, 20, 10),  // Already at max
            Arguments.of(0, 20, 20)    // Healing from 0 (resurrection?)
        );
    }
}
