package com.discordbot.battle.repository;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.service.CharacterValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for PlayerCharacterRepository.
 * Tests repository persistence layer for character CRUD operations.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.liquibase.enabled=false",
    "spring.jpa.show-sql=false",
    "spring.main.web-application-type=none"
})
@org.springframework.test.context.ActiveProfiles("repository-test")
class PlayerCharacterRepositoryTest {

    @Autowired
    private PlayerCharacterRepository repository;

    private BattleProperties properties;
    private CharacterValidationService validator;

    @BeforeEach
    void setUp() {
        properties = new BattleProperties();
        // Set required class configs
        properties.getClassConfig().getWarrior().setBaseHp(10);
        properties.getClassConfig().getRogue().setBaseHp(8);
        properties.getClassConfig().getMage().setBaseHp(6);
        properties.getClassConfig().getCleric().setBaseHp(8);
        
        validator = new CharacterValidationService(properties);
        // Clear any existing data
        repository.deleteAll();
    }

    @Test
    void save_validCharacter_persistsToDatabase() {
        PlayerCharacter character = createValidCharacter("user123", "guild456");

        PlayerCharacter saved = repository.save(character);

        assertNotNull(saved.getId(), "ID should be generated");
        assertNotNull(saved.getCreatedAt(), "CreatedAt should be set");
        assertNotNull(saved.getUpdatedAt(), "UpdatedAt should be set");
        assertEquals("user123", saved.getUserId());
        assertEquals("guild456", saved.getGuildId());
        assertEquals("Warrior", saved.getCharacterClass());
    }

    @Test
    void findByUserIdAndGuildId_characterExists_returnsCharacter() {
        PlayerCharacter character = createValidCharacter("user123", "guild456");
        repository.save(character);

        Optional<PlayerCharacter> found = repository.findByUserIdAndGuildId("user123", "guild456");

        assertTrue(found.isPresent());
        assertEquals("Warrior", found.get().getCharacterClass());
        assertEquals(15, found.get().getStrength());
    }

    @Test
    void findByUserIdAndGuildId_characterDoesNotExist_returnsEmpty() {
        Optional<PlayerCharacter> found = repository.findByUserIdAndGuildId("nonexistent", "guild999");

        assertFalse(found.isPresent());
    }

    @Test
    void findByUserIdAndGuildId_multipleGuilds_returnsCorrectCharacter() {
        // Same user, different guilds
        PlayerCharacter char1 = createValidCharacter("user123", "guild1");
        PlayerCharacter char2 = createValidCharacter("user123", "guild2");
        repository.save(char1);
        repository.save(char2);

        Optional<PlayerCharacter> found = repository.findByUserIdAndGuildId("user123", "guild2");

        assertTrue(found.isPresent());
        assertEquals("guild2", found.get().getGuildId());
    }

    @Test
    void findByGuildId_returnsAllCharactersInGuild() {
        PlayerCharacter char1 = createValidCharacter("user1", "guild456");
        PlayerCharacter char2 = createValidCharacter("user2", "guild456");
        PlayerCharacter char3 = createValidCharacter("user3", "guild789");
        repository.save(char1);
        repository.save(char2);
        repository.save(char3);

        List<PlayerCharacter> found = repository.findByGuildId("guild456");

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(c -> "guild456".equals(c.getGuildId())));
    }

    @Test
    void deleteByUserIdAndGuildId_removesCharacter() {
        PlayerCharacter character = createValidCharacter("user123", "guild456");
        repository.save(character);

        repository.deleteByUserIdAndGuildId("user123", "guild456");

        Optional<PlayerCharacter> found = repository.findByUserIdAndGuildId("user123", "guild456");
        assertFalse(found.isPresent());
    }

    @Test
    void update_existingCharacter_updatesFields() {
        PlayerCharacter character = createValidCharacter("user123", "guild456");
        repository.save(character);

        // Update the character
        PlayerCharacter found = repository.findByUserIdAndGuildId("user123", "guild456").orElseThrow();
        found.setCharacterClass("Rogue");
        found.setStrength(10);
        found.setDexterity(15);
        repository.save(found);

        // Verify updates
        PlayerCharacter updated = repository.findByUserIdAndGuildId("user123", "guild456").orElseThrow();
        assertEquals("Rogue", updated.getCharacterClass());
        assertEquals(10, updated.getStrength());
        assertEquals(15, updated.getDexterity());
        assertTrue(updated.getUpdatedAt().isAfter(updated.getCreatedAt()) || 
                   updated.getUpdatedAt().isEqual(updated.getCreatedAt()));
    }

    /**
     * Helper to create a valid character that passes validation.
     * Uses 27-point budget: 15+14+13+12+10+8 = 27 points
     */
    private PlayerCharacter createValidCharacter(String userId, String guildId) {
        PlayerCharacter character = new PlayerCharacter(
            userId, guildId,
            "Warrior", "Human",
            15, 14, 13, 12, 10, 8
        );
        assertTrue(validator.isValid(character), "Test character should be valid");
        return character;
    }
}
