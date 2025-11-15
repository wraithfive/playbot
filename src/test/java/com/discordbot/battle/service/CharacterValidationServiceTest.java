package com.discordbot.battle.service;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for CharacterValidationService.
 * This is security-critical validation logic that must be thoroughly tested.
 */
class CharacterValidationServiceTest {

    private CharacterValidationService validationService;
    private BattleProperties battleProperties;

    @BeforeEach
    void setUp() {
        // Set up battle properties with standard D&D 5e point-buy rules
        battleProperties = new BattleProperties();

        var character = new BattleProperties.CharacterProperties();
        var pointBuy = new BattleProperties.PointBuyProperties();
        pointBuy.setTotalPoints(27);
        pointBuy.setMinScore(8);
        pointBuy.setMaxScore(15);
        // Standard D&D 5e point-buy costs: 8=0, 9=1, 10=2, 11=3, 12=4, 13=5, 14=7, 15=9
        pointBuy.setCosts(List.of(0, 1, 2, 3, 4, 5, 7, 9));
        character.setPointBuy(pointBuy);

        battleProperties.setCharacter(character);

        validationService = new CharacterValidationService(battleProperties);
    }

    @Test
    @DisplayName("Valid character with exactly 27 points should pass validation")
    void testValidCharacter() {
        // 15(9) + 14(7) + 13(5) + 12(4) + 10(2) + 8(0) = 27 points
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678", // valid Discord snowflake
            "987654321098765432", // valid Discord snowflake
            "Warrior",
            "Human",
            15, // STR
            14, // DEX
            13, // CON
            12, // INT
            10, // WIS
            8   // CHA
        );

        assertTrue(validationService.isValid(character), "Character with 27 points should be valid");
    }

    @Test
    @DisplayName("Null character should fail validation")
    void testNullCharacter() {
        assertFalse(validationService.isValid(null), "Null character should be invalid");
    }

    @Test
    @DisplayName("Character with invalid class should fail validation")
    void testInvalidClass() {
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "InvalidClass", // Invalid class
            "Human",
            15, 14, 13, 12, 10, 8
        );

        assertFalse(validationService.isValid(character), "Character with invalid class should be invalid");
    }

    @Test
    @DisplayName("Character with invalid race should fail validation")
    void testInvalidRace() {
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "InvalidRace", // Invalid race
            15, 14, 13, 12, 10, 8
        );

        assertFalse(validationService.isValid(character), "Character with invalid race should be invalid");
    }

    @Test
    @DisplayName("Character with score below minimum (7) should fail validation")
    void testScoreBelowMinimum() {
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            7, // Below minimum (8)
            14, 13, 12, 10, 8
        );

        assertFalse(validationService.isValid(character), "Character with score below minimum should be invalid");
    }

    @Test
    @DisplayName("Character with score above maximum (16) should fail validation")
    void testScoreAboveMaximum() {
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            16, // Above maximum (15)
            14, 13, 12, 10, 8
        );

        assertFalse(validationService.isValid(character), "Character with score above maximum should be invalid");
    }

    @Test
    @DisplayName("Character with too many points (over budget) should fail validation")
    void testOverBudget() {
        // 15(9) + 15(9) + 15(9) + 15(9) + 15(9) + 15(9) = 54 points (way over 27)
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            15, 15, 15, 15, 15, 15
        );

        assertFalse(validationService.isValid(character), "Character with too many points should be invalid");
    }

    @Test
    @DisplayName("Character with too few points (under budget) should fail validation")
    void testUnderBudget() {
        // 8(0) + 8(0) + 8(0) + 8(0) + 8(0) + 8(0) = 0 points (under 27)
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            8, 8, 8, 8, 8, 8
        );

        assertFalse(validationService.isValid(character), "Character with too few points should be invalid");
    }

    @Test
    @DisplayName("Character with exactly budget (27 points) should pass validation")
    void testExactBudget() {
        // 14(7) + 14(7) + 14(7) + 10(2) + 10(2) + 10(2) = 27 points
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            14, 14, 14, 10, 10, 10
        );

        assertTrue(validationService.isValid(character), "Character with exactly 27 points should be valid");
    }

    @Test
    @DisplayName("Character with minimum scores (all 8s) should fail (0 points != 27)")
    void testAllMinimumScores() {
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            8, 8, 8, 8, 8, 8
        );

        int total = validationService.calculatePointBuyTotal(character);
        assertEquals(0, total, "All 8s should cost 0 points");
        assertFalse(validationService.isValid(character), "All 8s should be invalid (0 != 27 points)");
    }

    @Test
    @DisplayName("Character with maximum scores (all 15s) should fail (54 points != 27)")
    void testAllMaximumScores() {
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            15, 15, 15, 15, 15, 15
        );

        int total = validationService.calculatePointBuyTotal(character);
        assertEquals(54, total, "All 15s should cost 54 points");
        assertFalse(validationService.isValid(character), "All 15s should be invalid (54 != 27 points)");
    }

    @Test
    @DisplayName("Point-buy calculation should handle null character gracefully")
    void testCalculatePointBuyTotalWithNull() {
        int total = validationService.calculatePointBuyTotal(null);
        assertEquals(0, total, "Null character should return 0 points");
    }

    @Test
    @DisplayName("isValidClass should accept valid class names")
    void testValidClasses() {
        assertTrue(validationService.isValidClass("Warrior"), "Warrior should be valid");
        assertTrue(validationService.isValidClass("Rogue"), "Rogue should be valid");
        assertTrue(validationService.isValidClass("Mage"), "Mage should be valid");
        assertTrue(validationService.isValidClass("Cleric"), "Cleric should be valid");
    }

    @Test
    @DisplayName("isValidClass should reject invalid class names")
    void testInvalidClasses() {
        assertFalse(validationService.isValidClass("InvalidClass"), "InvalidClass should be invalid");
        assertFalse(validationService.isValidClass(""), "Empty string should be invalid");
        assertFalse(validationService.isValidClass(null), "Null should be invalid");
        assertFalse(validationService.isValidClass("warrior"), "Lowercase should be invalid (case-sensitive)");
    }

    @Test
    @DisplayName("isValidRace should accept valid race names")
    void testValidRaces() {
        assertTrue(validationService.isValidRace("Human"), "Human should be valid");
        assertTrue(validationService.isValidRace("Elf"), "Elf should be valid");
        assertTrue(validationService.isValidRace("Dwarf"), "Dwarf should be valid");
        assertTrue(validationService.isValidRace("Halfling"), "Halfling should be valid");
    }

    @Test
    @DisplayName("isValidRace should reject invalid race names")
    void testInvalidRaces() {
        assertFalse(validationService.isValidRace("InvalidRace"), "InvalidRace should be invalid");
        assertFalse(validationService.isValidRace(""), "Empty string should be invalid");
        assertFalse(validationService.isValidRace(null), "Null should be invalid");
        assertFalse(validationService.isValidRace("human"), "Lowercase should be invalid (case-sensitive)");
    }

    @Test
    @DisplayName("Balanced stat distribution with 27 points should pass")
    void testBalancedDistribution() {
        // 13(5) + 13(5) + 13(5) + 12(4) + 12(4) + 12(4) = 27 points
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            13, 13, 13, 12, 12, 12
        );

        assertTrue(validationService.isValid(character), "Balanced distribution with 27 points should be valid");
    }

    @Test
    @DisplayName("Min-max stat distribution with 27 points should pass")
    void testMinMaxDistribution() {
        // 15(9) + 15(9) + 15(9) + 8(0) + 8(0) + 8(0) = 27 points
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            15, 15, 15, 8, 8, 8
        );

        assertTrue(validationService.isValid(character), "Min-max distribution with 27 points should be valid");
    }

    @Test
    @DisplayName("Character with score at exact minimum (8) should be valid if total is 27")
    void testBoundaryMinimumScore() {
        // 15(9) + 14(7) + 13(5) + 12(4) + 10(2) + 8(0) = 27 points
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            15, 14, 13, 12, 10, 8
        );

        assertTrue(validationService.isValid(character), "Score of 8 (minimum) should be valid");
    }

    @Test
    @DisplayName("Character with score at exact maximum (15) should be valid if total is 27")
    void testBoundaryMaximumScore() {
        // 15(9) + 14(7) + 13(5) + 12(4) + 10(2) + 8(0) = 27 points
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            15, 14, 13, 12, 10, 8
        );

        assertTrue(validationService.isValid(character), "Score of 15 (maximum) should be valid");
    }

    @Test
    @DisplayName("Point-buy cost calculation should be accurate for various scores")
    void testPointBuyCostCalculation() {
        // Test individual score costs
        PlayerCharacter char8 = new PlayerCharacter("123456789012345678", "987654321098765432", "Warrior", "Human", 8, 8, 8, 8, 8, 8);
        assertEquals(0, validationService.calculatePointBuyTotal(char8), "6x 8 should cost 0 points");

        PlayerCharacter char10 = new PlayerCharacter("123456789012345678", "987654321098765432", "Warrior", "Human", 10, 10, 10, 10, 10, 10);
        assertEquals(12, validationService.calculatePointBuyTotal(char10), "6x 10 should cost 12 points (6*2)");

        PlayerCharacter char13 = new PlayerCharacter("123456789012345678", "987654321098765432", "Warrior", "Human", 13, 13, 13, 13, 13, 13);
        assertEquals(30, validationService.calculatePointBuyTotal(char13), "6x 13 should cost 30 points (6*5)");
    }

    @Test
    @DisplayName("Edge case: Character with one expensive stat (14 or 15) and rest cheap")
    void testOneExpensiveStat() {
        // 15(9) + 9(1) + 9(1) + 9(1) + 9(1) + 14(7) = 20 points (under budget)
        PlayerCharacter character = new PlayerCharacter(
            "123456789012345678",
            "987654321098765432",
            "Warrior",
            "Human",
            15, 9, 9, 9, 9, 14
        );

        int total = validationService.calculatePointBuyTotal(character);
        assertEquals(20, total, "15(9) + 14(7) + 4x9(4) should equal 20 points");
        assertFalse(validationService.isValid(character), "20 points != 27, should be invalid");
    }
}
