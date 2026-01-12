package com.discordbot.battle.service;

import static com.discordbot.battle.entity.PlayerCharacterTestFactory.create;

import com.discordbot.battle.entity.*;
import com.discordbot.battle.repository.CharacterAbilityCooldownRepository;
import com.discordbot.battle.repository.CharacterSpellSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SpellResourceService.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class SpellResourceServiceTest {

    @Mock
    private CharacterSpellSlotRepository spellSlotRepository;

    @Mock
    private CharacterAbilityCooldownRepository cooldownRepository;

    private SpellResourceService service;

    private PlayerCharacter mageCharacter;
    private PlayerCharacter warriorCharacter;
    private Ability spellAbility;
    private Ability cooldownAbility;

    @BeforeEach
    void setUp() {
        service = new SpellResourceService(spellSlotRepository, cooldownRepository);

        // Create test characters with standard ability scores (10 = +0 modifier)
        mageCharacter = create("user123", "guild456", "Mage", "Human", 
                                           8, 10, 10, 15, 12, 10); // High INT for Mage
        warriorCharacter = create("user789", "guild456", "Warrior", "Dwarf",
                                              15, 10, 14, 8, 10, 10); // High STR/CON for Warrior

        // Create test abilities
        spellAbility = new Ability("fireball", "Fireball", "SPELL", "Mage", 1, "", "DAMAGE+6", "Fire spell");
        spellAbility.setSpellSlotLevel(3);

        cooldownAbility = new Ability("second-wind", "Second Wind", "SKILL", "Warrior", 1, "", "HEAL+10", "Heal ability");
        cooldownAbility.setCooldownSeconds(60);
    }

    // --- Spell Slot Initialization Tests ---

    @Test
    void initializeSpellSlots_forMage_createsLevel1Slots() {
        when(spellSlotRepository.findByCharacterOrderBySlotLevel(mageCharacter))
                .thenReturn(Collections.emptyList());

        service.initializeSpellSlots(mageCharacter);

        ArgumentCaptor<CharacterSpellSlot> captor = ArgumentCaptor.forClass(CharacterSpellSlot.class);
        verify(spellSlotRepository).save(captor.capture());

        CharacterSpellSlot savedSlot = captor.getValue();
        assertEquals(1, savedSlot.getSlotLevel());
        assertEquals(2, savedSlot.getMaxSlots());
        assertEquals(2, savedSlot.getCurrentSlots());
    }

    @Test
    void initializeSpellSlots_forCleric_createsLevel1Slots() {
        PlayerCharacter cleric = create("user999", "guild456", "Cleric", "Human",
                                                     10, 10, 12, 10, 15, 10); // High WIS for Cleric
        when(spellSlotRepository.findByCharacterOrderBySlotLevel(cleric))
                .thenReturn(Collections.emptyList());

        service.initializeSpellSlots(cleric);

        ArgumentCaptor<CharacterSpellSlot> captor = ArgumentCaptor.forClass(CharacterSpellSlot.class);
        verify(spellSlotRepository).save(captor.capture());

        CharacterSpellSlot savedSlot = captor.getValue();
        assertEquals(1, savedSlot.getSlotLevel());
        assertEquals(2, savedSlot.getMaxSlots());
    }

    @Test
    void initializeSpellSlots_forWarrior_doesNotCreateSlots() {
        service.initializeSpellSlots(warriorCharacter);

        verify(spellSlotRepository, never()).save(any());
    }

    @Test
    void initializeSpellSlots_whenSlotsExist_skipsInitialization() {
        CharacterSpellSlot existingSlot = new CharacterSpellSlot(mageCharacter, 1, 2);
        when(spellSlotRepository.findByCharacterOrderBySlotLevel(mageCharacter))
                .thenReturn(List.of(existingSlot));

        service.initializeSpellSlots(mageCharacter);

        verify(spellSlotRepository, never()).save(any());
    }

    // --- Spell Slot Availability Tests ---

    @Test
    void hasAvailableSpellSlot_withAvailableSlots_returnsTrue() {
        CharacterSpellSlot slot = new CharacterSpellSlot(mageCharacter, 1, 2);
        when(spellSlotRepository.findByCharacterAndSlotLevel(mageCharacter, 1))
                .thenReturn(Optional.of(slot));

        assertTrue(service.hasAvailableSpellSlot(mageCharacter, 1));
    }

    @Test
    void hasAvailableSpellSlot_withNoSlots_returnsFalse() {
        CharacterSpellSlot slot = new CharacterSpellSlot(mageCharacter, 1, 0);
        when(spellSlotRepository.findByCharacterAndSlotLevel(mageCharacter, 1))
                .thenReturn(Optional.of(slot));

        assertFalse(service.hasAvailableSpellSlot(mageCharacter, 1));
    }

    @Test
    void hasAvailableSpellSlot_whenSlotDoesNotExist_returnsFalse() {
        when(spellSlotRepository.findByCharacterAndSlotLevel(mageCharacter, 5))
                .thenReturn(Optional.empty());

        assertFalse(service.hasAvailableSpellSlot(mageCharacter, 5));
    }

    // --- Spell Slot Consumption Tests ---

    @Test
    void consumeSpellSlot_withAvailableSlot_consumesAndReturnsTrue() {
        CharacterSpellSlot slot = new CharacterSpellSlot(mageCharacter, 1, 2);
        when(spellSlotRepository.findByCharacterAndSlotLevel(mageCharacter, 1))
                .thenReturn(Optional.of(slot));

        boolean result = service.consumeSpellSlot(mageCharacter, 1);

        assertTrue(result);
        assertEquals(1, slot.getCurrentSlots());
        verify(spellSlotRepository).save(slot);
    }

    @Test
    void consumeSpellSlot_withNoSlotsRemaining_returnsFalse() {
        CharacterSpellSlot slot = new CharacterSpellSlot(mageCharacter, 1, 0);
        when(spellSlotRepository.findByCharacterAndSlotLevel(mageCharacter, 1))
                .thenReturn(Optional.of(slot));

        boolean result = service.consumeSpellSlot(mageCharacter, 1);

        assertFalse(result);
        verify(spellSlotRepository, never()).save(any());
    }

    @Test
    void consumeSpellSlot_whenSlotDoesNotExist_returnsFalse() {
        when(spellSlotRepository.findByCharacterAndSlotLevel(mageCharacter, 5))
                .thenReturn(Optional.empty());

        boolean result = service.consumeSpellSlot(mageCharacter, 5);

        assertFalse(result);
        verify(spellSlotRepository, never()).save(any());
    }

    // --- Spell Slot Restoration Tests ---

    @Test
    void restoreSpellSlots_afterLongRest_restoresAllSlots() {
        CharacterSpellSlot slot1 = new CharacterSpellSlot(mageCharacter, 1, 2);
        slot1.consumeSlot();
        CharacterSpellSlot slot2 = new CharacterSpellSlot(mageCharacter, 2, 3);
        slot2.consumeSlot();
        slot2.consumeSlot();

        when(spellSlotRepository.findByCharacterOrderBySlotLevel(mageCharacter))
                .thenReturn(List.of(slot1, slot2));

        service.restoreSpellSlots(mageCharacter, RestType.LONG_REST);

        assertEquals(2, slot1.getCurrentSlots());
        assertEquals(3, slot2.getCurrentSlots());
        verify(spellSlotRepository, times(2)).save(any());
    }

    @Test
    void restoreSpellSlots_withRestTypeNone_doesNotRestore() {
        service.restoreSpellSlots(mageCharacter, RestType.NONE);

        verify(spellSlotRepository, never()).findByCharacterOrderBySlotLevel(any());
        verify(spellSlotRepository, never()).save(any());
    }

    @Test
    void restoreSpellSlots_afterShortRest_doesNotRestoreYet() {
        CharacterSpellSlot slot = new CharacterSpellSlot(mageCharacter, 1, 2);
        slot.consumeSlot();
        when(spellSlotRepository.findByCharacterOrderBySlotLevel(mageCharacter))
                .thenReturn(List.of(slot));

        service.restoreSpellSlots(mageCharacter, RestType.SHORT_REST);

        // Short rest restoration not yet implemented (Warlock feature)
        assertEquals(1, slot.getCurrentSlots()); // Unchanged
        verify(spellSlotRepository, never()).save(any());
    }

    @Test
    void restoreSpellSlots_withNoSlots_doesNothing() {
        when(spellSlotRepository.findByCharacterOrderBySlotLevel(warriorCharacter))
                .thenReturn(Collections.emptyList());

        service.restoreSpellSlots(warriorCharacter, RestType.LONG_REST);

        verify(spellSlotRepository, never()).save(any());
    }

    // --- Cooldown Availability Tests ---

    @Test
    void isAbilityAvailable_withNoCooldown_returnsTrue() {
        Ability noCooldownAbility = new Ability("passive", "Passive", "TALENT", null, 1, "", "AC+1", "Passive");

        boolean result = service.isAbilityAvailable(warriorCharacter, noCooldownAbility);

        assertTrue(result);
        verify(cooldownRepository, never()).findByCharacterAndAbility(any(), any());
    }

    @Test
    void isAbilityAvailable_neverUsed_returnsTrue() {
        when(cooldownRepository.findByCharacterAndAbility(warriorCharacter, cooldownAbility))
                .thenReturn(Optional.empty());

        boolean result = service.isAbilityAvailable(warriorCharacter, cooldownAbility);

        assertTrue(result);
    }

    @Test
    void isAbilityAvailable_withExpiredCooldown_returnsTrue() {
        CharacterAbilityCooldown expiredCooldown = new CharacterAbilityCooldown(warriorCharacter, cooldownAbility, 1);
        // Wait for cooldown to expire
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        when(cooldownRepository.findByCharacterAndAbility(warriorCharacter, cooldownAbility))
                .thenReturn(Optional.of(expiredCooldown));

        boolean result = service.isAbilityAvailable(warriorCharacter, cooldownAbility);

        assertTrue(result);
    }

    @Test
    void isAbilityAvailable_withActiveCooldown_returnsFalse() {
        CharacterAbilityCooldown activeCooldown = new CharacterAbilityCooldown(warriorCharacter, cooldownAbility, 3600);

        when(cooldownRepository.findByCharacterAndAbility(warriorCharacter, cooldownAbility))
                .thenReturn(Optional.of(activeCooldown));

        boolean result = service.isAbilityAvailable(warriorCharacter, cooldownAbility);

        assertFalse(result);
    }

    // --- Cooldown Start/Reset Tests ---

    @Test
    void startAbilityCooldown_createsNewCooldown() {
        when(cooldownRepository.findByCharacterAndAbility(warriorCharacter, cooldownAbility))
                .thenReturn(Optional.empty());

        service.startAbilityCooldown(warriorCharacter, cooldownAbility);

        ArgumentCaptor<CharacterAbilityCooldown> captor = ArgumentCaptor.forClass(CharacterAbilityCooldown.class);
        verify(cooldownRepository).save(captor.capture());

        CharacterAbilityCooldown savedCooldown = captor.getValue();
        assertNotNull(savedCooldown.getLastUsed());
        assertNotNull(savedCooldown.getAvailableAt());
        assertTrue(savedCooldown.getAvailableAt().isAfter(savedCooldown.getLastUsed()));
    }

    @Test
    void startAbilityCooldown_resetsExistingCooldown() {
        CharacterAbilityCooldown existingCooldown = new CharacterAbilityCooldown(warriorCharacter, cooldownAbility, 60);
        LocalDateTime originalLastUsed = existingCooldown.getLastUsed();

        when(cooldownRepository.findByCharacterAndAbility(warriorCharacter, cooldownAbility))
                .thenReturn(Optional.of(existingCooldown));

        // Wait a bit to ensure timestamps differ
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        service.startAbilityCooldown(warriorCharacter, cooldownAbility);

        assertTrue(existingCooldown.getLastUsed().isAfter(originalLastUsed));
        verify(cooldownRepository).save(existingCooldown);
    }

    @Test
    void startAbilityCooldown_withNoCooldown_doesNothing() {
        Ability noCooldownAbility = new Ability("passive", "Passive", "TALENT", null, 1, "", "AC+1", "Passive");

        service.startAbilityCooldown(warriorCharacter, noCooldownAbility);

        verify(cooldownRepository, never()).save(any());
    }

    // --- Cooldown Expiry Tests ---

    @Test
    void getAbilityCooldownExpiry_withActiveCooldown_returnsTimestamp() {
        CharacterAbilityCooldown cooldown = new CharacterAbilityCooldown(warriorCharacter, cooldownAbility, 60);
        when(cooldownRepository.findByCharacterAndAbility(warriorCharacter, cooldownAbility))
                .thenReturn(Optional.of(cooldown));

        Optional<LocalDateTime> expiry = service.getAbilityCooldownExpiry(warriorCharacter, cooldownAbility);

        assertTrue(expiry.isPresent());
        assertEquals(cooldown.getAvailableAt(), expiry.get());
    }

    @Test
    void getAbilityCooldownExpiry_withNoCooldown_returnsEmpty() {
        when(cooldownRepository.findByCharacterAndAbility(warriorCharacter, cooldownAbility))
                .thenReturn(Optional.empty());

        Optional<LocalDateTime> expiry = service.getAbilityCooldownExpiry(warriorCharacter, cooldownAbility);

        assertFalse(expiry.isPresent());
    }

    // --- Cleanup Tests ---

    @Test
    void cleanupExpiredCooldowns_removesOldCooldowns() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        List<CharacterAbilityCooldown> expiredCooldowns = List.of(
                new CharacterAbilityCooldown(warriorCharacter, cooldownAbility, 60),
                new CharacterAbilityCooldown(mageCharacter, spellAbility, 120)
        );

        when(cooldownRepository.findByAvailableAtBefore(cutoff))
                .thenReturn(expiredCooldowns);

        int count = service.cleanupExpiredCooldowns(cutoff);

        assertEquals(2, count);
        verify(cooldownRepository).deleteAll(expiredCooldowns);
    }

    @Test
    void cleanupExpiredCooldowns_withNoneExpired_doesNotDelete() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        when(cooldownRepository.findByAvailableAtBefore(cutoff))
                .thenReturn(Collections.emptyList());

        int count = service.cleanupExpiredCooldowns(cutoff);

        assertEquals(0, count);
        verify(cooldownRepository, never()).deleteAll(any());
    }

    // --- Get Spell Slots Tests ---

    @Test
    void getSpellSlots_returnsAllSlots() {
        CharacterSpellSlot slot1 = new CharacterSpellSlot(mageCharacter, 1, 2);
        CharacterSpellSlot slot2 = new CharacterSpellSlot(mageCharacter, 2, 3);
        List<CharacterSpellSlot> slots = List.of(slot1, slot2);

        when(spellSlotRepository.findByCharacterOrderBySlotLevel(mageCharacter))
                .thenReturn(slots);

        List<CharacterSpellSlot> result = service.getSpellSlots(mageCharacter);

        assertEquals(2, result.size());
        assertEquals(slot1, result.get(0));
        assertEquals(slot2, result.get(1));
    }
}
