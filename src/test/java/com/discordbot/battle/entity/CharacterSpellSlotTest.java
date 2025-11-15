package com.discordbot.battle.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CharacterSpellSlot entity validation and business logic.
 */
class CharacterSpellSlotTest {

    @Test
    void consumeSlot_withAvailableSlots_returnsTrue() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 2);
        
        assertTrue(slot.consumeSlot());
        assertEquals(1, slot.getCurrentSlots());
    }

    @Test
    void consumeSlot_untilEmpty_eventuallyReturnsFalse() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 2);
        
        assertTrue(slot.consumeSlot()); // 2 -> 1
        assertTrue(slot.consumeSlot()); // 1 -> 0
        assertFalse(slot.consumeSlot()); // Already at 0
        assertEquals(0, slot.getCurrentSlots());
    }

    @Test
    void consumeSlot_whenAlreadyEmpty_returnsFalse() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 0);
        
        assertFalse(slot.consumeSlot());
        assertEquals(0, slot.getCurrentSlots());
    }

    @Test
    void hasAvailableSlots_withSlots_returnsTrue() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 3);
        
        assertTrue(slot.hasAvailableSlots());
    }

    @Test
    void hasAvailableSlots_withoutSlots_returnsFalse() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 0);
        
        assertFalse(slot.hasAvailableSlots());
    }

    @Test
    void restoreSlots_resetsToMax() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 3);
        slot.consumeSlot();
        slot.consumeSlot();
        assertEquals(1, slot.getCurrentSlots());
        
        LocalDateTime beforeRestore = LocalDateTime.now().minusSeconds(1);
        slot.restoreSlots();
        
        assertEquals(3, slot.getCurrentSlots());
        assertTrue(slot.getLastRest().isAfter(beforeRestore));
    }

    @Test
    void setMaxSlots_withValidValue_succeeds() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 2);
        
        slot.setMaxSlots(5);
        
        assertEquals(5, slot.getMaxSlots());
        assertEquals(2, slot.getCurrentSlots()); // Current unchanged
    }

    @Test
    void setMaxSlots_belowCurrentSlots_adjustsCurrentDown() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 5);
        assertEquals(5, slot.getCurrentSlots());
        
        slot.setMaxSlots(3);
        
        assertEquals(3, slot.getMaxSlots());
        assertEquals(3, slot.getCurrentSlots()); // Auto-adjusted down
    }

    @Test
    void setMaxSlots_negative_throwsException() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 2);
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> slot.setMaxSlots(-1));
        assertTrue(ex.getMessage().contains("cannot be negative"));
    }

    @Test
    void setCurrentSlots_withinBounds_succeeds() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 5);
        
        slot.setCurrentSlots(3);
        
        assertEquals(3, slot.getCurrentSlots());
    }

    @Test
    void setCurrentSlots_negative_throwsException() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 5);
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> slot.setCurrentSlots(-1));
        assertTrue(ex.getMessage().contains("cannot be negative"));
    }

    @Test
    void setCurrentSlots_exceedingMax_throwsException() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 1, 5);
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> slot.setCurrentSlots(10));
        assertTrue(ex.getMessage().contains("cannot exceed max slots"));
    }

    @Test
    void constructor_initializesWithFullSlots() {
        CharacterSpellSlot slot = new CharacterSpellSlot(null, 3, 4);
        
        assertEquals(3, slot.getSlotLevel());
        assertEquals(4, slot.getMaxSlots());
        assertEquals(4, slot.getCurrentSlots()); // Starts full
        assertNotNull(slot.getLastRest());
    }
}
