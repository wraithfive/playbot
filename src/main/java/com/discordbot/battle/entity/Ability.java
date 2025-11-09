package com.discordbot.battle.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a learnable ability (talent, skill, spell) in the battle system.
 * Abilities are metadata; a character learning an ability is represented by {@link CharacterAbility}.
 *
 * Design goals:
 * - Single unified table instead of separate Talent/Skill/Spell tables.
 * - Type field distinguishes category (TALENT/SKILL/SPELL) for filtering and UI grouping.
 * - Optional classRestriction limits ability to specific character classes; null means any class.
 * - Prerequisites expressed as a comma-separated list of ability keys (lightweight until we need a join table).
 * - Effects stored as a short expression string (e.g., "DAMAGE+3", "CRIT_CHANCE+5", "ON_HIT:BLEED").
 *   A future parser can convert these into structured modifiers; initial implementation treats them as opaque.
 */
@Entity
@Table(name = "ability",
       uniqueConstraints = @UniqueConstraint(name = "uk_ability_key", columnNames = "ability_key"),
       indexes = {
           @Index(name = "idx_ability_type", columnList = "type"),
           @Index(name = "idx_ability_class", columnList = "class_restriction")
       })
public class Ability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stable programmatic key (lowercase, kebab or snake) used for prerequisites and internal lookups.
     */
    @Column(name = "ability_key", nullable = false, length = 64)
    private String key;

    /** Human-readable display name */
    @Column(nullable = false, length = 100)
    private String name;

    /** Enum-like categorical type: TALENT, SKILL, SPELL */
    @Column(nullable = false, length = 16)
    private String type;

    /** Optional: class restriction (e.g., Warrior, Rogue). Null means available to all classes. */
    @Column(name = "class_restriction", length = 50)
    private String classRestriction;

    /** Optional tier or level gating (0+). */
    @Column(name = "required_level", nullable = false)
    private int requiredLevel;

    /** Comma-separated prerequisite ability keys. Lightweight representation; empty string means none. */
    @Column(name = "prerequisites", length = 512, nullable = false)
    private String prerequisites;

    /** Short effect expression string (opaque to DB). */
    @Column(name = "effect", length = 256, nullable = false)
    private String effect;

    /** Flavor or description for help embeds. */
    @Column(name = "description", length = 512, nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Ability() {
        // JPA
    }

    public Ability(String key, String name, String type, String classRestriction, int requiredLevel,
                   String prerequisites, String effect, String description) {
        this.key = key;
        this.name = name;
        this.type = type;
        this.classRestriction = classRestriction;
        this.requiredLevel = requiredLevel;
        this.prerequisites = prerequisites == null ? "" : prerequisites;
        this.effect = effect;
        this.description = description;
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

    // Getters
    public Long getId() { return id; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getClassRestriction() { return classRestriction; }
    public int getRequiredLevel() { return requiredLevel; }
    public String getPrerequisites() { return prerequisites; }
    public String getEffect() { return effect; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Mutators (if we later allow balance patches via admin tooling)
    public void setDescription(String description) { this.description = description; }
    public void setEffect(String effect) { this.effect = effect; }
    public void setRequiredLevel(int requiredLevel) { this.requiredLevel = requiredLevel; }
}
