package com.discordbot.battle.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CharacterAbilityCooldown entity validation and business logic.
 */
class CharacterAbilityCooldownTest {

    @Test
    void isAvailable_withPastTimestamp_returnsTrue() {
        // Create cooldown with 1 second duration and wait for it to expire
        CharacterAbilityCooldown cooldown = new CharacterAbilityCooldown(null, null, 1);
        try {
            Thread.sleep(1100); // Wait for cooldown to expire
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertTrue(cooldown.isAvailable());
    }

    @Test
    void isAvailable_withFutureTimestamp_returnsFalse() {
        CharacterAbilityCooldown cooldown = new CharacterAbilityCooldown(null, null, 3600);
        
        assertFalse(cooldown.isAvailable());
    }

    @Test
    void isAvailable_withExactCurrentTime_returnsTrue() {
        // Create cooldown with 0 seconds - should be immediately available
        CharacterAbilityCooldown cooldown = new CharacterAbilityCooldown(null, null, 0);
        
        assertTrue(cooldown.isAvailable());
    }

    @Test
    void resetCooldown_updatesLastUsedAndAvailableAt() {
        CharacterAbilityCooldown cooldown = new CharacterAbilityCooldown(null, null, 60);
        LocalDateTime originalLastUsed = cooldown.getLastUsed();
        LocalDateTime originalAvailableAt = cooldown.getAvailableAt();
        
        // Wait a tiny bit to ensure timestamps differ
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        cooldown.resetCooldown(120); // 2 minutes
        
        assertTrue(cooldown.getLastUsed().isAfter(originalLastUsed));
        assertTrue(cooldown.getAvailableAt().isAfter(originalAvailableAt));
        assertTrue(cooldown.getAvailableAt().isAfter(cooldown.getLastUsed()));
    }

    @Test
    void resetCooldown_setsCorrectDuration() {
        CharacterAbilityCooldown cooldown = new CharacterAbilityCooldown(null, null, 60);
        
        cooldown.resetCooldown(300); // 5 minutes
        
        long secondsDiff = java.time.Duration.between(
            cooldown.getLastUsed(), 
            cooldown.getAvailableAt()
        ).getSeconds();
        
        assertEquals(300, secondsDiff);
    }

    @Test
    void constructor_initializesWithCooldownActive() {
        LocalDateTime beforeCreation = LocalDateTime.now();
        CharacterAbilityCooldown cooldown = new CharacterAbilityCooldown(null, null, 3600);
        LocalDateTime afterCreation = LocalDateTime.now();
        
        assertNotNull(cooldown.getLastUsed());
        assertNotNull(cooldown.getAvailableAt());
        
        // lastUsed should be around now
        assertTrue(cooldown.getLastUsed().isAfter(beforeCreation.minusSeconds(1)));
        assertTrue(cooldown.getLastUsed().isBefore(afterCreation.plusSeconds(1)));
        
        // availableAt should be 3600 seconds in the future
        LocalDateTime expectedAvailable = cooldown.getLastUsed().plusSeconds(3600);
        assertTrue(cooldown.getAvailableAt().isAfter(expectedAvailable.minusSeconds(1)));
        assertTrue(cooldown.getAvailableAt().isBefore(expectedAvailable.plusSeconds(1)));
    }

    @Test
    void constructor_withZeroCooldown_isImmediatelyAvailable() {
        CharacterAbilityCooldown cooldown = new CharacterAbilityCooldown(null, null, 0);
        
        assertTrue(cooldown.isAvailable());
    }
}
