package com.discordbot.battle.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Tracks per-character spell slot availability for spellcasting classes (Mage, Cleric).
 * D&D 5e has 9 spell slot levels; characters gain slots as they level up.
 * 
 * For MVP (level 1 characters):
 * - Mage (Wizard): 2 × level 1 slots
 * - Cleric: 2 × level 1 slots
 * 
 * Slots are restored after a long rest.
 */
@Entity
@Table(name = "character_spell_slots",
       uniqueConstraints = @UniqueConstraint(name = "uk_character_spell_slot", 
                                             columnNames = {"character_id", "slot_level"}),
       indexes = {
           @Index(name = "idx_character_spell_slots_character", columnList = "character_id")
       })
public class CharacterSpellSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    @NotNull
    private PlayerCharacter character;

    /** Spell slot level (1-9 in D&D 5e) */
    @Column(name = "slot_level", nullable = false)
    @Min(1)
    @Max(9)
    private int slotLevel;

    /** Maximum slots available for this level (determined by character level and class) */
    @Column(name = "max_slots", nullable = false)
    @Min(0)
    private int maxSlots;

    /** Currently available slots (decreases when spell is cast, resets on long rest) */
    @Column(name = "current_slots", nullable = false)
    @Min(0)
    private int currentSlots;

    @Column(name = "last_rest", nullable = false)
    private LocalDateTime lastRest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CharacterSpellSlot() {
        // JPA
    }

    public CharacterSpellSlot(PlayerCharacter character, int slotLevel, int maxSlots) {
        this.character = character;
        this.slotLevel = slotLevel;
        this.maxSlots = maxSlots;
        this.currentSlots = maxSlots; // Start with full slots
        this.lastRest = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Consume a spell slot if available.
     * @return true if slot was available and consumed, false otherwise
     */
    public boolean consumeSlot() {
        if (currentSlots > 0) {
            currentSlots--;
            return true;
        }
        return false;
    }

    /**
     * Restore all spell slots to maximum (e.g., after a long rest).
     */
    public void restoreSlots() {
        currentSlots = maxSlots;
        lastRest = LocalDateTime.now();
    }

    /**
     * Check if any slots are available.
     */
    public boolean hasAvailableSlots() {
        return currentSlots > 0;
    }

    // Getters
    public Long getId() { return id; }
    public PlayerCharacter getCharacter() { return character; }
    public int getSlotLevel() { return slotLevel; }
    public int getMaxSlots() { return maxSlots; }
    public int getCurrentSlots() { return currentSlots; }
    public LocalDateTime getLastRest() { return lastRest; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setMaxSlots(int maxSlots) { 
        if (maxSlots < 0) {
            throw new IllegalArgumentException("Max slots cannot be negative");
        }
        this.maxSlots = maxSlots;
        // If current slots exceed new max, adjust them down
        if (this.currentSlots > maxSlots) {
            this.currentSlots = maxSlots;
        }
    }
    
    public void setCurrentSlots(int currentSlots) { 
        if (currentSlots < 0) {
            throw new IllegalArgumentException("Current slots cannot be negative");
        }
        if (currentSlots > maxSlots) {
            throw new IllegalArgumentException("Current slots cannot exceed max slots (current=" + currentSlots + ", max=" + maxSlots + ")");
        }
        this.currentSlots = currentSlots; 
    }
}
