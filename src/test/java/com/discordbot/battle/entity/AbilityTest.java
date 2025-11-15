package com.discordbot.battle.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Ability entity validation in setters.
 */
class AbilityTest {

    @Test
    void setSpellSlotLevel_withValidValue_succeeds() {
        Ability ability = new Ability("test", "Test", "SPELL", null, 1, "", "DAMAGE+3", "Test spell");
        
        ability.setSpellSlotLevel(5);
        
        assertEquals(5, ability.getSpellSlotLevel());
    }

    @Test
    void setSpellSlotLevel_withZero_succeeds() {
        Ability ability = new Ability("test", "Test", "SPELL", null, 1, "", "DAMAGE+3", "Test cantrip");
        
        ability.setSpellSlotLevel(0);
        
        assertEquals(0, ability.getSpellSlotLevel());
    }

    @Test
    void setSpellSlotLevel_withNine_succeeds() {
        Ability ability = new Ability("test", "Test", "SPELL", null, 1, "", "DAMAGE+3", "Test 9th level spell");
        
        ability.setSpellSlotLevel(9);
        
        assertEquals(9, ability.getSpellSlotLevel());
    }

    @Test
    void setSpellSlotLevel_withNull_succeeds() {
        Ability ability = new Ability("test", "Test", "TALENT", null, 1, "", "DAMAGE+3", "Test talent");
        
        ability.setSpellSlotLevel(null);
        
        assertNull(ability.getSpellSlotLevel());
    }

    @Test
    void setSpellSlotLevel_negative_throwsException() {
        Ability ability = new Ability("test", "Test", "SPELL", null, 1, "", "DAMAGE+3", "Test spell");
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ability.setSpellSlotLevel(-1));
        assertTrue(ex.getMessage().contains("between 0 and 9"));
        assertTrue(ex.getMessage().contains("-1"));
    }

    @Test
    void setSpellSlotLevel_tooHigh_throwsException() {
        Ability ability = new Ability("test", "Test", "SPELL", null, 1, "", "DAMAGE+3", "Test spell");
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ability.setSpellSlotLevel(10));
        assertTrue(ex.getMessage().contains("between 0 and 9"));
        assertTrue(ex.getMessage().contains("10"));
    }

    @Test
    void setCooldownSeconds_withValidValue_succeeds() {
        Ability ability = new Ability("test", "Test", "SKILL", null, 1, "", "DAMAGE+3", "Test skill");
        
        ability.setCooldownSeconds(60);
        
        assertEquals(60, ability.getCooldownSeconds());
    }

    @Test
    void setCooldownSeconds_withZero_succeeds() {
        Ability ability = new Ability("test", "Test", "SKILL", null, 1, "", "DAMAGE+3", "Test skill");
        
        ability.setCooldownSeconds(0);
        
        assertEquals(0, ability.getCooldownSeconds());
    }

    @Test
    void setCooldownSeconds_withNull_succeeds() {
        Ability ability = new Ability("test", "Test", "TALENT", null, 1, "", "DAMAGE+3", "Test talent");
        
        ability.setCooldownSeconds(null);
        
        assertNull(ability.getCooldownSeconds());
    }

    @Test
    void setCooldownSeconds_negative_throwsException() {
        Ability ability = new Ability("test", "Test", "SKILL", null, 1, "", "DAMAGE+3", "Test skill");
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ability.setCooldownSeconds(-10));
        assertTrue(ex.getMessage().contains("cannot be negative"));
        assertTrue(ex.getMessage().contains("-10"));
    }

    @Test
    void setChargesMax_withValidValue_succeeds() {
        Ability ability = new Ability("test", "Test", "SKILL", null, 1, "", "DAMAGE+3", "Test skill");
        
        ability.setChargesMax(3);
        
        assertEquals(3, ability.getChargesMax());
    }

    @Test
    void setChargesMax_withOne_succeeds() {
        Ability ability = new Ability("test", "Test", "SKILL", null, 1, "", "DAMAGE+3", "Test skill");
        
        ability.setChargesMax(1);
        
        assertEquals(1, ability.getChargesMax());
    }

    @Test
    void setChargesMax_withNull_succeeds() {
        Ability ability = new Ability("test", "Test", "TALENT", null, 1, "", "DAMAGE+3", "Test talent");
        
        ability.setChargesMax(null);
        
        assertNull(ability.getChargesMax());
    }

    @Test
    void setChargesMax_zero_throwsException() {
        Ability ability = new Ability("test", "Test", "SKILL", null, 1, "", "DAMAGE+3", "Test skill");
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ability.setChargesMax(0));
        assertTrue(ex.getMessage().contains("must be at least 1"));
        assertTrue(ex.getMessage().contains("0"));
    }

    @Test
    void setChargesMax_negative_throwsException() {
        Ability ability = new Ability("test", "Test", "SKILL", null, 1, "", "DAMAGE+3", "Test skill");
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ability.setChargesMax(-5));
        assertTrue(ex.getMessage().contains("must be at least 1"));
        assertTrue(ex.getMessage().contains("-5"));
    }

    @Test
    void setRestType_withValidEnum_succeeds() {
        Ability ability = new Ability("test", "Test", "SPELL", null, 1, "", "DAMAGE+3", "Test spell");
        
        ability.setRestType(RestType.LONG_REST);
        
        assertEquals(RestType.LONG_REST, ability.getRestType());
    }

    @Test
    void setRestType_withNull_succeeds() {
        Ability ability = new Ability("test", "Test", "TALENT", null, 1, "", "DAMAGE+3", "Test talent");
        
        ability.setRestType(null);
        
        assertNull(ability.getRestType());
    }

    @Test
    void constructor_initializesFieldsCorrectly() {
        Ability ability = new Ability(
            "fireball",
            "Fireball",
            "SPELL",
            "Mage",
            5,
            "arcane-focus",
            "DAMAGE+6,AOE",
            "A powerful area-of-effect fire spell"
        );
        
        assertEquals("fireball", ability.getKey());
        assertEquals("Fireball", ability.getName());
        assertEquals("SPELL", ability.getType());
        assertEquals("Mage", ability.getClassRestriction());
        assertEquals(5, ability.getRequiredLevel());
        assertEquals("arcane-focus", ability.getPrerequisites());
        assertEquals("DAMAGE+6,AOE", ability.getEffect());
        assertEquals("A powerful area-of-effect fire spell", ability.getDescription());
        
        // Resource costs should default to null
        assertNull(ability.getSpellSlotLevel());
        assertNull(ability.getCooldownSeconds());
        assertNull(ability.getChargesMax());
        assertNull(ability.getRestType());
    }
}
