package com.discordbot.battle.service;

import com.discordbot.battle.entity.Ability;
import com.discordbot.battle.entity.CharacterAbility;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.entity.PlayerCharacterTestFactory;
import com.discordbot.battle.repository.AbilityRepository;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AbilityService.
 * Tests ability listing and learning with class restrictions and prerequisites.
 */
@SuppressWarnings({"null", "unused"})
class AbilityServiceTest {

    @Mock
    private AbilityRepository abilityRepository;

    @Mock
    private CharacterAbilityRepository characterAbilityRepository;

    @Mock
    private PlayerCharacterRepository playerCharacterRepository;

    private AbilityService abilityService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        abilityService = new AbilityService(
            abilityRepository,
            characterAbilityRepository,
            playerCharacterRepository
        );
    }

    @Test
    void listAvailableForCharacter_showsAllUnrestrictedAbilities() {
        // Given: Character and unrestricted abilities
        PlayerCharacter warrior = createWarrior("user1");

        Ability heal = createAbility(1L, "heal", "Heal", null);
        Ability shield = createAbility(2L, "shield", "Shield", null);

        when(abilityRepository.findAll()).thenReturn(List.of(heal, shield));
        when(characterAbilityRepository.findByCharacter(warrior)).thenReturn(List.of());

        // When: List available abilities
        List<Ability> available = abilityService.listAvailableForCharacter(warrior);

        // Then: Shows all unrestricted abilities
        assertEquals(2, available.size());
        assertTrue(available.contains(heal));
        assertTrue(available.contains(shield));
    }

    @Test
    void listAvailableForCharacter_filtersClassRestrictedAbilities() {
        // Given: Warrior character with mixed class abilities
        PlayerCharacter warrior = createWarrior("user1");

        Ability powerAttack = createAbility(1L, "power_attack", "Power Attack", "Warrior");
        Ability sneak = createAbility(2L, "sneak", "Sneak", "Rogue");
        Ability fireball = createAbility(3L, "fireball", "Fireball", "Wizard");
        Ability heal = createAbility(4L, "heal", "Heal", null); // Unrestricted

        when(abilityRepository.findAll()).thenReturn(List.of(powerAttack, sneak, fireball, heal));
        when(characterAbilityRepository.findByCharacter(warrior)).thenReturn(List.of());

        // When: List available abilities
        List<Ability> available = abilityService.listAvailableForCharacter(warrior);

        // Then: Only shows Warrior abilities and unrestricted
        assertEquals(2, available.size());
        assertTrue(available.contains(powerAttack));
        assertTrue(available.contains(heal));
        assertFalse(available.contains(sneak));
        assertFalse(available.contains(fireball));
    }

    @Test
    void listAvailableForCharacter_excludesAlreadyLearned() {
        // Given: Warrior with one learned ability
        PlayerCharacter warrior = createWarrior("user1");

        Ability powerAttack = createAbility(1L, "power_attack", "Power Attack", "Warrior");
        Ability cleave = createAbility(2L, "cleave", "Cleave", "Warrior");

        CharacterAbility learnedPowerAttack = new CharacterAbility(warrior, powerAttack);

        when(abilityRepository.findAll()).thenReturn(List.of(powerAttack, cleave));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of(learnedPowerAttack));

        // When: List available abilities
        List<Ability> available = abilityService.listAvailableForCharacter(warrior);

        // Then: Excludes already learned ability
        assertEquals(1, available.size());
        assertTrue(available.contains(cleave));
        assertFalse(available.contains(powerAttack));
    }

    @Test
    void listAvailableForCharacter_caseInsensitiveClassMatch() {
        // Given: Character class in mixed case
        PlayerCharacter warrior = createWarrior("user1");

        Ability ability1 = createAbility(1L, "ability1", "Ability 1", "WARRIOR"); // Uppercase
        Ability ability2 = createAbility(2L, "ability2", "Ability 2", "warrior"); // Lowercase
        Ability ability3 = createAbility(3L, "ability3", "Ability 3", "Warrior"); // Mixed

        when(abilityRepository.findAll()).thenReturn(List.of(ability1, ability2, ability3));
        when(characterAbilityRepository.findByCharacter(warrior)).thenReturn(List.of());

        // When: List available abilities
        List<Ability> available = abilityService.listAvailableForCharacter(warrior);

        // Then: All variations match (case-insensitive)
        assertEquals(3, available.size());
    }

    @Test
    void learnAbility_successfullyLearnsAbility() {
        // Given: Character and available ability
        PlayerCharacter warrior = createWarrior("user1");
        Ability powerAttack = createAbility(1L, "power_attack", "Power Attack", "Warrior");

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("power_attack"))
            .thenReturn(Optional.of(powerAttack));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of());

        // When: Learn ability
        CharacterAbility result = abilityService.learnAbility("guild1", "user1", "power_attack");

        // Then: Ability is learned
        assertNotNull(result);
        assertEquals(warrior, result.getCharacter());
        assertEquals(powerAttack, result.getAbility());

        ArgumentCaptor<CharacterAbility> captor = ArgumentCaptor.forClass(CharacterAbility.class);
        verify(characterAbilityRepository).save(captor.capture());
        assertEquals(powerAttack, captor.getValue().getAbility());
    }

    @Test
    void learnAbility_throwsWhenCharacterNotFound() {
        // Given: No character exists
        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.empty());

        // When/Then: Throws exception
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> abilityService.learnAbility("guild1", "user1", "power_attack"));

        assertEquals("You need a character first.", ex.getMessage());
        verify(abilityRepository, never()).findByKey(any());
    }

    @Test
    void learnAbility_throwsWhenAbilityNotFound() {
        // Given: Character exists but ability doesn't
        PlayerCharacter warrior = createWarrior("user1");

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("unknown_ability"))
            .thenReturn(Optional.empty());

        // When/Then: Throws exception
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> abilityService.learnAbility("guild1", "user1", "unknown_ability"));

        assertEquals("Ability not found: unknown_ability", ex.getMessage());
        verify(characterAbilityRepository, never()).save(any());
    }

    @Test
    void learnAbility_throwsWhenClassRestrictionViolated() {
        // Given: Warrior trying to learn Rogue ability
        PlayerCharacter warrior = createWarrior("user1");
        Ability sneak = createAbility(1L, "sneak", "Sneak", "Rogue");

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("sneak"))
            .thenReturn(Optional.of(sneak));

        // When/Then: Throws exception
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> abilityService.learnAbility("guild1", "user1", "sneak"));

        assertEquals("Ability restricted to class: Rogue", ex.getMessage());
        verify(characterAbilityRepository, never()).save(any());
    }

    @Test
    void learnAbility_throwsWhenAlreadyLearned() {
        // Given: Character already knows the ability
        PlayerCharacter warrior = createWarrior("user1");
        Ability powerAttack = createAbility(1L, "power_attack", "Power Attack", "Warrior");
        CharacterAbility existing = new CharacterAbility(warrior, powerAttack);

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("power_attack"))
            .thenReturn(Optional.of(powerAttack));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of(existing));

        // When/Then: Throws exception
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> abilityService.learnAbility("guild1", "user1", "power_attack"));

        assertEquals("Already learned: Power Attack", ex.getMessage());
        verify(characterAbilityRepository, never()).save(any());
    }

    @Test
    void learnAbility_throwsWhenPrerequisitesMissing() {
        // Given: Ability requires prerequisite that character doesn't have
        PlayerCharacter warrior = createWarrior("user1");
        Ability greatCleave = createAbilityWithPrerequisites(3L, "great_cleave", "Great Cleave", "Warrior", "power_attack,cleave");

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("great_cleave"))
            .thenReturn(Optional.of(greatCleave));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of()); // Has no abilities

        // When/Then: Throws exception
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> abilityService.learnAbility("guild1", "user1", "great_cleave"));

        assertTrue(ex.getMessage().contains("Missing prerequisites"));
        assertTrue(ex.getMessage().contains("power_attack"));
        assertTrue(ex.getMessage().contains("cleave"));
    }

    @Test
    void learnAbility_successWhenPrerequisitesMet() {
        // Given: Character has all prerequisites
        PlayerCharacter warrior = createWarrior("user1");

        Ability powerAttack = createAbility(1L, "power_attack", "Power Attack", "Warrior");
        Ability cleave = createAbility(2L, "cleave", "Cleave", "Warrior");
        Ability greatCleave = createAbilityWithPrerequisites(3L, "great_cleave", "Great Cleave", "Warrior", "power_attack,cleave");

        CharacterAbility hasPowerAttack = new CharacterAbility(warrior, powerAttack);
        CharacterAbility hasCleave = new CharacterAbility(warrior, cleave);

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("great_cleave"))
            .thenReturn(Optional.of(greatCleave));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of(hasPowerAttack, hasCleave));

        // When: Learn ability
        CharacterAbility result = abilityService.learnAbility("guild1", "user1", "great_cleave");

        // Then: Successfully learned
        assertNotNull(result);
        assertEquals(greatCleave, result.getAbility());
        verify(characterAbilityRepository).save(any(CharacterAbility.class));
    }

    @Test
    void learnAbility_successWhenNoPrerequisites() {
        // Given: Ability with empty prerequisites (empty string means none)
        PlayerCharacter warrior = createWarrior("user1");
        Ability basicStrike = createAbility(1L, "basic_strike", "Basic Strike", null);

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("basic_strike"))
            .thenReturn(Optional.of(basicStrike));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of());

        // When: Learn ability
        CharacterAbility result = abilityService.learnAbility("guild1", "user1", "basic_strike");

        // Then: Successfully learned
        assertNotNull(result);
        verify(characterAbilityRepository).save(any(CharacterAbility.class));
    }

    @Test
    void learnAbility_successWhenEmptyPrerequisites() {
        // Given: Ability with blank prerequisites string
        PlayerCharacter warrior = createWarrior("user1");
        Ability basicStrike = createAbilityWithPrerequisites(1L, "basic_strike", "Basic Strike", null, "  ");

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("basic_strike"))
            .thenReturn(Optional.of(basicStrike));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of());

        // When: Learn ability
        CharacterAbility result = abilityService.learnAbility("guild1", "user1", "basic_strike");

        // Then: Successfully learned
        assertNotNull(result);
        verify(characterAbilityRepository).save(any(CharacterAbility.class));
    }

    @Test
    void learnAbility_handlesSinglePrerequisite() {
        // Given: Ability with one prerequisite
        PlayerCharacter warrior = createWarrior("user1");

        Ability powerAttack = createAbility(1L, "power_attack", "Power Attack", "Warrior");
        Ability improvedPowerAttack = createAbilityWithPrerequisites(2L, "improved_power_attack", "Improved Power Attack", "Warrior", "power_attack");

        CharacterAbility hasPowerAttack = new CharacterAbility(warrior, powerAttack);

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("improved_power_attack"))
            .thenReturn(Optional.of(improvedPowerAttack));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of(hasPowerAttack));

        // When: Learn ability
        CharacterAbility result = abilityService.learnAbility("guild1", "user1", "improved_power_attack");

        // Then: Successfully learned
        assertNotNull(result);
        verify(characterAbilityRepository).save(any(CharacterAbility.class));
    }

    @Test
    void learnAbility_partialPrerequisitesFails() {
        // Given: Character has only some prerequisites
        PlayerCharacter warrior = createWarrior("user1");

        Ability powerAttack = createAbility(1L, "power_attack", "Power Attack", "Warrior");
        Ability cleave = createAbility(2L, "cleave", "Cleave", "Warrior");
        Ability greatCleave = createAbilityWithPrerequisites(3L, "great_cleave", "Great Cleave", "Warrior", "power_attack,cleave");

        CharacterAbility hasPowerAttack = new CharacterAbility(warrior, powerAttack);
        // Missing cleave

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("great_cleave"))
            .thenReturn(Optional.of(greatCleave));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of(hasPowerAttack));

        // When/Then: Throws exception
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> abilityService.learnAbility("guild1", "user1", "great_cleave"));

        assertTrue(ex.getMessage().contains("Missing prerequisites"));
        assertTrue(ex.getMessage().contains("cleave"));
        assertFalse(ex.getMessage().contains("power_attack"),
            "Should not list power_attack as missing since it's learned");
    }

    @Test
    void learnAbility_handlesPrerequisitesWithExtraWhitespace() {
        // Given: Prerequisites with extra whitespace
        PlayerCharacter warrior = createWarrior("user1");

        Ability prereq1 = createAbility(1L, "prereq1", "Prereq 1", "Warrior");
        Ability prereq2 = createAbility(2L, "prereq2", "Prereq 2", "Warrior");
        Ability advanced = createAbilityWithPrerequisites(3L, "advanced", "Advanced", "Warrior", "  prereq1  ,  prereq2  ");

        CharacterAbility has1 = new CharacterAbility(warrior, prereq1);
        CharacterAbility has2 = new CharacterAbility(warrior, prereq2);

        when(playerCharacterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(warrior));
        when(abilityRepository.findByKey("advanced"))
            .thenReturn(Optional.of(advanced));
        when(characterAbilityRepository.findByCharacter(warrior))
            .thenReturn(List.of(has1, has2));

        // When: Learn ability
        CharacterAbility result = abilityService.learnAbility("guild1", "user1", "advanced");

        // Then: Successfully learned (whitespace properly trimmed)
        assertNotNull(result);
        verify(characterAbilityRepository).save(any(CharacterAbility.class));
    }

    /**
     * Helper: Create a Warrior character
     */
    private PlayerCharacter createWarrior(String userId) {
        return PlayerCharacterTestFactory.create(
            userId, "guild1", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
    }

    /**
     * Helper: Create an Ability using the public constructor with ID set via reflection
     */
    private Ability createAbility(Long id, String key, String name, String classRestriction) {
        Ability ability = new Ability(key, name, "TALENT", classRestriction, 1, "", "Test effect", "Test description");
        // Set ID via reflection for testing
        if (id != null) {
            try {
                Field idField = Ability.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(ability, id);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ability ID for testing", e);
            }
        }
        return ability;
    }

    /**
     * Helper: Create an Ability with specific prerequisites and ID set via reflection
     */
    private Ability createAbilityWithPrerequisites(Long id, String key, String name, String classRestriction, String prerequisites) {
        Ability ability = new Ability(key, name, "TALENT", classRestriction, 1, prerequisites, "Test effect", "Test description");
        // Set ID via reflection for testing
        if (id != null) {
            try {
                Field idField = Ability.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(ability, id);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ability ID for testing", e);
            }
        }
        return ability;
    }
}
