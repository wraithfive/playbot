package com.discordbot.battle.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks per-character, per-ability cooldowns.
 * Used for abilities/spells that have cooldown timers between uses.
 */
@Entity
@Table(name = "character_ability_cooldown",
       uniqueConstraints = @UniqueConstraint(name = "uk_character_ability_cooldown", 
                                             columnNames = {"character_id", "ability_id"}),
       indexes = {
           @Index(name = "idx_character_ability_cooldown_character", columnList = "character_id"),
           @Index(name = "idx_character_ability_cooldown_ability", columnList = "ability_id"),
           @Index(name = "idx_character_ability_cooldown_available_at", columnList = "available_at")
       })
public class CharacterAbilityCooldown {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private PlayerCharacter character;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ability_id", nullable = false)
    private Ability ability;

    /** When the ability was last used */
    @Column(name = "last_used", nullable = false)
    private LocalDateTime lastUsed;

    /** When the cooldown expires and ability becomes available again */
    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CharacterAbilityCooldown() {
        // JPA
    }

    public CharacterAbilityCooldown(PlayerCharacter character, Ability ability, int cooldownSeconds) {
        this.character = character;
        this.ability = ability;
        this.lastUsed = LocalDateTime.now();
        this.availableAt = this.lastUsed.plusSeconds(cooldownSeconds);
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
     * Check if the cooldown has expired and ability is available for use.
     */
    public boolean isAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(availableAt);
    }

    /**
     * Update cooldown when ability is used again.
     */
    public void resetCooldown(int cooldownSeconds) {
        this.lastUsed = LocalDateTime.now();
        this.availableAt = this.lastUsed.plusSeconds(cooldownSeconds);
    }

    // Getters
    public Long getId() { return id; }
    public PlayerCharacter getCharacter() { return character; }
    public Ability getAbility() { return ability; }
    public LocalDateTime getLastUsed() { return lastUsed; }
    public LocalDateTime getAvailableAt() { return availableAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
