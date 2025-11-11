package com.discordbot.battle.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    // --- Resource Costs (Phase 2b) ---

    /**
     * Spell slot level (1-9 for D&D 5e spells, 0 for cantrips).
     * NULL for talents/skills that don't use spell slots.
     */
    @Column(name = "spell_slot_level")
    @Min(0)
    @Max(9)
    private Integer spellSlotLevel;

    /**
     * Cooldown in seconds between uses. NULL means no cooldown.
     */
    @Column(name = "cooldown_seconds")
    @Min(0)
    private Integer cooldownSeconds;

    /**
     * Maximum charges per rest. NULL means no charge system (unlimited uses if no spell slot cost).
     */
    @Column(name = "charges_max")
    @Min(1)
    private Integer chargesMax;

    /**
     * Rest type required to restore charges/slots.
     * NULL means not applicable (e.g., passive talents).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rest_type", length = 16)
    private RestType restType;

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
    public Integer getSpellSlotLevel() { return spellSlotLevel; }
    public Integer getCooldownSeconds() { return cooldownSeconds; }
    public Integer getChargesMax() { return chargesMax; }
    public RestType getRestType() { return restType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Mutators (if we later allow balance patches via admin tooling)
    public void setDescription(String description) { this.description = description; }
    public void setEffect(String effect) { this.effect = effect; }
    public void setRequiredLevel(int requiredLevel) { this.requiredLevel = requiredLevel; }
    
    public void setSpellSlotLevel(Integer spellSlotLevel) {
        if (spellSlotLevel != null && (spellSlotLevel < 0 || spellSlotLevel > 9)) {
            throw new IllegalArgumentException("Spell slot level must be between 0 and 9, got: " + spellSlotLevel);
        }
        this.spellSlotLevel = spellSlotLevel;
    }
    
    public void setCooldownSeconds(Integer cooldownSeconds) {
        if (cooldownSeconds != null && cooldownSeconds < 0) {
            throw new IllegalArgumentException("Cooldown seconds cannot be negative, got: " + cooldownSeconds);
        }
        this.cooldownSeconds = cooldownSeconds;
    }
    
    public void setChargesMax(Integer chargesMax) {
        if (chargesMax != null && chargesMax < 1) {
            throw new IllegalArgumentException("Charges max must be at least 1, got: " + chargesMax);
        }
        this.chargesMax = chargesMax;
    }
    
    public void setRestType(RestType restType) { this.restType = restType; }
}
