package com.discordbot.battle.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Join entity linking a PlayerCharacter to a learned Ability.
 */
@Entity
@Table(name = "character_ability",
       uniqueConstraints = @UniqueConstraint(name = "uk_character_ability_unique", columnNames = {"character_id", "ability_id"}),
       indexes = {
           @Index(name = "idx_character_ability_character", columnList = "character_id"),
           @Index(name = "idx_character_ability_ability", columnList = "ability_id")
       })
public class CharacterAbility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false, foreignKey = @ForeignKey(name = "fk_character_ability_character"))
    private PlayerCharacter character;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ability_id", nullable = false, foreignKey = @ForeignKey(name = "fk_character_ability_ability"))
    private Ability ability;

    @Column(name = "learned_at", nullable = false, updatable = false)
    private LocalDateTime learnedAt;

    protected CharacterAbility() {}

    public CharacterAbility(PlayerCharacter character, Ability ability) {
        this.character = character;
        this.ability = ability;
    }

    @PrePersist
    void onCreate() {
        learnedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public PlayerCharacter getCharacter() { return character; }
    public Ability getAbility() { return ability; }
    public LocalDateTime getLearnedAt() { return learnedAt; }
}
