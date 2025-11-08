package com.discordbot.battle.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * D&D 5e inspired character entity for battle system.
 * Validation is handled by CharacterValidationService using configurable rules.
 * 
 * Each user can have one character per guild.
 */
@Entity
@Table(name = "player_character", 
       uniqueConstraints = @UniqueConstraint(name = "uk_player_character_user_guild", 
                                            columnNames = {"userId", "guildId"}),
       indexes = @Index(name = "idx_player_character_guild", columnList = "guildId"))
public class PlayerCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Discord user ID (snowflake)
     */
    @Column(nullable = false, length = 20)
    private String userId;

    /**
     * Discord guild/server ID (snowflake)
     */
    @Column(nullable = false, length = 20)
    private String guildId;

    /**
     * Character class (Warrior, Rogue, Mage, Cleric)
     */
    @Column(nullable = false, length = 50)
    private String characterClass;

    /**
     * Character race (Human, Elf, Dwarf, Halfling)
     */
    @Column(nullable = false, length = 50)
    private String race;

    /**
     * D&D 5e ability scores (8-15 range by default)
     */
    @Column(nullable = false)
    private int strength;

    @Column(nullable = false)
    private int dexterity;

    @Column(nullable = false)
    private int constitution;

    @Column(nullable = false)
    private int intelligence;

    @Column(nullable = false)
    private int wisdom;

    @Column(nullable = false)
    private int charisma;

    /**
     * Timestamp when character was created
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when character was last updated
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Default constructor for JPA
     */
    protected PlayerCharacter() {
    }

    /**
     * Constructor for creating a new character
     */
    public PlayerCharacter(String userId, String guildId,
                          String characterClass, String race, 
                          int strength, int dexterity, int constitution,
                          int intelligence, int wisdom, int charisma) {
        this.userId = userId;
        this.guildId = guildId;
        this.characterClass = characterClass;
        this.race = race;
        this.strength = strength;
        this.dexterity = dexterity;
        this.constitution = constitution;
        this.intelligence = intelligence;
        this.wisdom = wisdom;
        this.charisma = charisma;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getCharacterClass() {
        return characterClass;
    }

    public String getRace() {
        return race;
    }

    public int getStrength() {
        return strength;
    }

    public int getDexterity() {
        return dexterity;
    }

    public int getConstitution() {
        return constitution;
    }

    public int getIntelligence() {
        return intelligence;
    }

    public int getWisdom() {
        return wisdom;
    }

    public int getCharisma() {
        return charisma;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Setters for updates (not for userId/guildId - those are immutable after creation)
    public void setCharacterClass(String characterClass) {
        this.characterClass = characterClass;
    }

    public void setRace(String race) {
        this.race = race;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }

    public void setDexterity(int dexterity) {
        this.dexterity = dexterity;
    }

    public void setConstitution(int constitution) {
        this.constitution = constitution;
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = intelligence;
    }

    public void setWisdom(int wisdom) {
        this.wisdom = wisdom;
    }

    public void setCharisma(int charisma) {
        this.charisma = charisma;
    }
}
