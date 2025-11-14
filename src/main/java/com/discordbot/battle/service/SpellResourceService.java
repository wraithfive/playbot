package com.discordbot.battle.service;

import com.discordbot.battle.entity.*;
import com.discordbot.battle.repository.CharacterAbilityCooldownRepository;
import com.discordbot.battle.repository.CharacterSpellSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing spell resource costs: spell slots, cooldowns, and charges.
 * Handles D&D 5e style resource management for the battle system.
 * 
 * Responsibilities:
 * - Initialize spell slots for spellcasting classes (Mage, Cleric)
 * - Track and validate spell slot consumption
 * - Restore spell slots after rests
 * - Track ability cooldowns
 * - Manage charge-based abilities (future)
 */
@Service
public class SpellResourceService {
    
    private static final Logger logger = LoggerFactory.getLogger(SpellResourceService.class);
    
    private final CharacterSpellSlotRepository spellSlotRepository;
    private final CharacterAbilityCooldownRepository cooldownRepository;
    
    public SpellResourceService(
            CharacterSpellSlotRepository spellSlotRepository,
            CharacterAbilityCooldownRepository cooldownRepository) {
        this.spellSlotRepository = spellSlotRepository;
        this.cooldownRepository = cooldownRepository;
    }
    
    /**
     * Initialize spell slots for a spellcasting character based on their class and level.
     * 
     * D&D 5e spell slot progression (MVP: level 1 only):
     * - Mage (Wizard): 2 × level 1 slots at level 1
     * - Cleric: 2 × level 1 slots at level 1
     * - Warrior/Rogue: No spell slots (may have special abilities with cooldowns)
     * 
     * @param character The character to initialize spell slots for
     */
    @Transactional
    public void initializeSpellSlots(PlayerCharacter character) {
        String characterClass = character.getCharacterClass();
        // MVP: all characters are level 1 (level field not yet implemented)
        int level = 1;
        
        logger.debug("Initializing spell slots for {} level {} {}", 
                     character.getUserId(), level, characterClass);
        
        // Only Mage and Cleric have spell slots
        if (!isSpellcaster(characterClass)) {
            logger.debug("Character class {} is not a spellcaster, skipping spell slot initialization", 
                        characterClass);
            return;
        }
        
        // Check if spell slots already exist
        List<CharacterSpellSlot> existingSlots = spellSlotRepository.findByCharacterOrderBySlotLevel(character);
        if (!existingSlots.isEmpty()) {
            logger.warn("Spell slots already exist for character {}, skipping initialization", 
                       character.getId());
            return;
        }
        
        // Initialize level 1 spell slots for level 1 characters
        // Future: expand to handle higher levels with more slot levels
        if (level == 1) {
            CharacterSpellSlot level1Slots = new CharacterSpellSlot(character, 1, 2);
            spellSlotRepository.save(level1Slots);
            logger.info("Initialized 2 × level 1 spell slots for character {}", character.getId());
        } else {
            // Future: implement full D&D 5e spell slot progression table
            logger.warn("Spell slot initialization for level {} not yet implemented", level);
        }
    }
    
    /**
     * Check if a character has an available spell slot of the specified level.
     * 
     * @param character The character to check
     * @param slotLevel The spell slot level (1-9)
     * @return true if the character has at least one available slot of that level
     */
    public boolean hasAvailableSpellSlot(PlayerCharacter character, int slotLevel) {
        return spellSlotRepository.findByCharacterAndSlotLevel(character, slotLevel)
                .map(CharacterSpellSlot::hasAvailableSlots)
                .orElse(false);
    }
    
    /**
     * Consume a spell slot when casting a spell.
     * 
     * @param character The character casting the spell
     * @param slotLevel The spell slot level to consume
     * @return true if the slot was successfully consumed, false if no slots available
     */
    @Transactional
    public boolean consumeSpellSlot(PlayerCharacter character, int slotLevel) {
        Optional<CharacterSpellSlot> slotOpt = spellSlotRepository.findByCharacterAndSlotLevel(character, slotLevel);
        
        if (slotOpt.isEmpty()) {
            logger.warn("Character {} does not have spell slots of level {}", 
                       character.getId(), slotLevel);
            return false;
        }
        
        CharacterSpellSlot slot = slotOpt.get();
        boolean consumed = slot.consumeSlot();
        
        if (consumed) {
            spellSlotRepository.save(slot);
            logger.debug("Character {} consumed level {} spell slot ({}/{} remaining)", 
                        character.getId(), slotLevel, slot.getCurrentSlots(), slot.getMaxSlots());
        } else {
            logger.debug("Character {} has no remaining level {} spell slots", 
                        character.getId(), slotLevel);
        }
        
        return consumed;
    }
    
    /**
     * Restore spell slots after a rest.
     * 
     * @param character The character taking a rest
     * @param restType The type of rest (SHORT_REST or LONG_REST)
     */
    @Transactional
    public void restoreSpellSlots(PlayerCharacter character, RestType restType) {
        if (restType == RestType.NONE) {
            logger.debug("RestType.NONE does not restore spell slots");
            return;
        }
        
        List<CharacterSpellSlot> slots = spellSlotRepository.findByCharacterOrderBySlotLevel(character);
        
        if (slots.isEmpty()) {
            logger.debug("Character {} has no spell slots to restore", character.getId());
            return;
        }
        
        // In D&D 5e:
        // - Long rest restores all spell slots for most classes
        // - Short rest restores spell slots for Warlock only
        // For MVP: only implement long rest restoration (standard for Mage/Cleric)
        if (restType == RestType.LONG_REST) {
            for (CharacterSpellSlot slot : slots) {
                slot.restoreSlots();
                spellSlotRepository.save(slot);
            }
            logger.info("Restored all spell slots for character {} after long rest", character.getId());
        } else if (restType == RestType.SHORT_REST) {
            // Future: implement Warlock short rest recovery
            logger.debug("Short rest spell slot restoration not yet implemented (Warlock feature)");
        }
    }
    
    /**
     * Check if an ability is off cooldown and available to use.
     * 
     * @param character The character using the ability
     * @param ability The ability to check
     * @return true if the ability is available (no active cooldown), false otherwise
     */
    public boolean isAbilityAvailable(PlayerCharacter character, Ability ability) {
        // If ability has no cooldown, it's always available (subject to other restrictions)
        if (ability.getCooldownSeconds() == null) {
            return true;
        }
        
        Optional<CharacterAbilityCooldown> cooldownOpt = 
                cooldownRepository.findByCharacterAndAbility(character, ability);
        
        // No cooldown record means ability hasn't been used yet
        if (cooldownOpt.isEmpty()) {
            return true;
        }
        
        return cooldownOpt.get().isAvailable();
    }
    
    /**
     * Start or reset the cooldown for an ability after it's been used.
     * 
     * @param character The character using the ability
     * @param ability The ability being used
     */
    @Transactional
    public void startAbilityCooldown(PlayerCharacter character, Ability ability) {
        Integer cooldownSeconds = ability.getCooldownSeconds();
        
        if (cooldownSeconds == null) {
            logger.debug("Ability {} has no cooldown, skipping cooldown tracking", ability.getKey());
            return;
        }
        
        Optional<CharacterAbilityCooldown> existingCooldown = 
                cooldownRepository.findByCharacterAndAbility(character, ability);
        
        if (existingCooldown.isPresent()) {
            // Update existing cooldown
            CharacterAbilityCooldown cooldown = existingCooldown.get();
            cooldown.resetCooldown(cooldownSeconds);
            cooldownRepository.save(cooldown);
            logger.debug("Reset cooldown for ability {} for character {}", 
                        ability.getKey(), character.getId());
        } else {
            // Create new cooldown record
            CharacterAbilityCooldown cooldown = new CharacterAbilityCooldown(character, ability, cooldownSeconds);
            cooldownRepository.save(cooldown);
            logger.debug("Started cooldown for ability {} for character {}", 
                        ability.getKey(), character.getId());
        }
    }
    
    /**
     * Get the timestamp when an ability's cooldown will expire.
     * 
     * @param character The character to check
     * @param ability The ability to check
     * @return The timestamp when the cooldown expires, or null if no active cooldown
     */
    public Optional<LocalDateTime> getAbilityCooldownExpiry(PlayerCharacter character, Ability ability) {
        return cooldownRepository.findByCharacterAndAbility(character, ability)
                .map(CharacterAbilityCooldown::getAvailableAt);
    }
    
    /**
     * Clean up expired cooldowns (for scheduled maintenance jobs).
     * Removes cooldown records that have expired to keep the table lean.
     * 
     * @param olderThan Remove cooldowns that expired before this timestamp
     * @return Number of cooldowns removed
     */
    @Transactional
    public int cleanupExpiredCooldowns(LocalDateTime olderThan) {
        List<CharacterAbilityCooldown> expiredCooldowns = 
                cooldownRepository.findByAvailableAtBefore(olderThan);
        
        int count = expiredCooldowns.size();
        
        if (count > 0) {
            cooldownRepository.deleteAll(expiredCooldowns);
            logger.info("Cleaned up {} expired cooldowns older than {}", count, olderThan);
        }
        
        return count;
    }
    
    /**
     * Get all spell slots for a character (for UI display).
     * 
     * @param character The character to get spell slots for
     * @return List of spell slots ordered by level
     */
    public List<CharacterSpellSlot> getSpellSlots(PlayerCharacter character) {
        return spellSlotRepository.findByCharacterOrderBySlotLevel(character);
    }
    
    /**
     * Check if a character class is a spellcaster.
     * 
     * @param characterClass The character class to check
     * @return true if the class can cast spells (Mage, Cleric)
     */
    private boolean isSpellcaster(String characterClass) {
        return "Mage".equalsIgnoreCase(characterClass) || 
               "Cleric".equalsIgnoreCase(characterClass);
    }
}
