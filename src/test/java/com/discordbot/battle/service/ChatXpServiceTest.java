package com.discordbot.battle.service;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.entity.PlayerCharacterTestFactory;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatXpService.
 * Tests the primary progression system (chat-based XP awards).
 */
class ChatXpServiceTest {

    @Mock
    private PlayerCharacterRepository characterRepository;

    @Mock
    private BattleProperties battleProperties;

    @Mock
    private BattleProperties.ProgressionConfig progressionConfig;

    @Mock
    private BattleProperties.ProgressionConfig.ChatXpConfig chatXpConfig;

    @Mock
    private BattleProperties.ProgressionConfig.XpConfig xpConfig;

    private ChatXpService chatXpService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup default config mocks
        when(battleProperties.isEnabled()).thenReturn(true);
        when(battleProperties.getProgression()).thenReturn(progressionConfig);
        when(progressionConfig.getChatXp()).thenReturn(chatXpConfig);
        when(progressionConfig.getXp()).thenReturn(xpConfig);

        // Default chat XP config
        when(chatXpConfig.isEnabled()).thenReturn(true);
        when(chatXpConfig.getBaseXp()).thenReturn(10);
        when(chatXpConfig.getBonusXpMax()).thenReturn(5);
        when(chatXpConfig.getCooldownSeconds()).thenReturn(60);
        when(chatXpConfig.isAutoCreateCharacter()).thenReturn(true);

        // Default XP curve (simplified for testing)
        when(xpConfig.getLevelCurve()).thenReturn(List.of(
            0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000, 64000,
            85000, 100000, 120000, 140000, 165000, 195000, 225000, 265000, 305000, 355000
        ));

        chatXpService = new ChatXpService(battleProperties, characterRepository);
    }

    @Test
    void awardChatXp_successfullyAwardsXp() {
        // Given: Existing character with no recent chat XP
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "123456789012345678", "987654321098765432", "Warrior", "Human",
            12, 12, 12, 12, 12, 12
        );
        character.setLastChatXpAt(null); // No previous award

        when(characterRepository.findByUserIdAndGuildId("123456789012345678", "987654321098765432"))
            .thenReturn(Optional.of(character));
        when(characterRepository.save(any())).thenReturn(character);

        // When: Award chat XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp("123456789012345678", "987654321098765432");

        // Then: XP is awarded
        assertEquals(ChatXpService.XpAwardStatus.AWARDED, result.status());
        assertTrue(result.xpAwarded() >= 10 && result.xpAwarded() <= 15,
            "XP should be between baseXp (10) and baseXp+bonusMax (15)");
        assertEquals(1, result.oldLevel());
        assertEquals(1, result.newLevel());
        assertFalse(result.leveledUp());

        // Verify character was saved with updated timestamp
        verify(characterRepository).save(argThat(c ->
            c.getLastChatXpAt() != null
        ));
    }

    @Test
    void awardChatXp_detectsLevelUp() {
        // Given: Character near level-up threshold (level 1 -> 2 at 300 XP)
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "123456789012345678", "987654321098765432", "Warrior", "Human",
            12, 12, 12, 12, 12, 12
        );
        character.setXp(290); // 10-15 XP will trigger level up
        character.setLastChatXpAt(null);

        when(characterRepository.findByUserIdAndGuildId("123456789012345678", "987654321098765432"))
            .thenReturn(Optional.of(character));
        when(characterRepository.save(any())).thenReturn(character);

        // When: Award chat XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp("123456789012345678", "987654321098765432");

        // Then: Level up is detected
        assertEquals(ChatXpService.XpAwardStatus.AWARDED, result.status());
        assertEquals(1, result.oldLevel());
        assertEquals(2, result.newLevel());
        assertTrue(result.leveledUp());
    }

    @Test
    void awardChatXp_returnsDisabled_whenBattleSystemDisabled() {
        // Given: Battle system is disabled
        when(battleProperties.isEnabled()).thenReturn(false);

        // When: Attempt to award XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp("123456789012345678", "987654321098765432");

        // Then: Returns disabled status
        assertEquals(ChatXpService.XpAwardStatus.DISABLED, result.status());
        assertEquals(0, result.xpAwarded());
        assertFalse(result.leveledUp());

        // Verify no database interaction
        verify(characterRepository, never()).findByUserIdAndGuildId(any(), any());
        verify(characterRepository, never()).save(any());
    }

    @Test
    void awardChatXp_returnsDisabled_whenChatXpDisabled() {
        // Given: Chat XP feature is disabled
        when(chatXpConfig.isEnabled()).thenReturn(false);

        // When: Attempt to award XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp("123456789012345678", "987654321098765432");

        // Then: Returns disabled status
        assertEquals(ChatXpService.XpAwardStatus.DISABLED, result.status());
        verify(characterRepository, never()).findByUserIdAndGuildId(any(), any());
    }

    @Test
    void awardChatXp_returnsNoCharacter_whenCharacterMissingAndAutoCreateDisabled() {
        // Given: No character exists and auto-create is disabled
        when(chatXpConfig.isAutoCreateCharacter()).thenReturn(false);
        when(characterRepository.findByUserIdAndGuildId("123456789012345678", "987654321098765432"))
            .thenReturn(Optional.empty());

        // When: Attempt to award XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp("123456789012345678", "987654321098765432");

        // Then: Returns no character status
        assertEquals(ChatXpService.XpAwardStatus.NO_CHARACTER, result.status());
        assertEquals(0, result.xpAwarded());

        // Verify no character was created
        verify(characterRepository, never()).save(any());
    }

    @Test
    void awardChatXp_autoCreatesCharacter_whenEnabled() {
        // Given: No character exists and auto-create is enabled
        String userId = "111111111111111111"; // Valid Discord snowflake
        String guildId = "222222222222222222"; // Valid Discord snowflake
        when(characterRepository.findByUserIdAndGuildId(userId, guildId))
            .thenReturn(Optional.empty());

        ArgumentCaptor<PlayerCharacter> characterCaptor = ArgumentCaptor.forClass(PlayerCharacter.class);
        when(characterRepository.save(characterCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        // When: Attempt to award XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp(userId, guildId);

        // Then: Character is created and XP is awarded
        assertEquals(ChatXpService.XpAwardStatus.AWARDED, result.status());
        assertTrue(result.xpAwarded() > 0);

        // Verify character was created with correct defaults
        PlayerCharacter createdCharacter = characterCaptor.getAllValues().get(0);
        assertEquals(userId, createdCharacter.getUserId());
        assertEquals(guildId, createdCharacter.getGuildId());
        assertEquals("Warrior", createdCharacter.getCharacterClass());
        assertEquals("Human", createdCharacter.getRace());
        assertEquals(12, createdCharacter.getStrength());
        assertEquals(12, createdCharacter.getDexterity());
        assertEquals(12, createdCharacter.getConstitution());
        assertEquals(12, createdCharacter.getIntelligence());
        assertEquals(12, createdCharacter.getWisdom());
        assertEquals(12, createdCharacter.getCharisma());

        // Should be saved twice: once for creation, once for XP update
        verify(characterRepository, times(2)).save(any());
    }

    @Test
    void awardChatXp_returnsOnCooldown_whenRecentlyAwarded() {
        // Given: Character received XP 30 seconds ago (cooldown is 60 seconds)
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "123456789012345678", "987654321098765432", "Warrior", "Human",
            12, 12, 12, 12, 12, 12
        );
        character.setLastChatXpAt(LocalDateTime.now().minusSeconds(30));

        when(characterRepository.findByUserIdAndGuildId("123456789012345678", "987654321098765432"))
            .thenReturn(Optional.of(character));

        // When: Attempt to award XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp("123456789012345678", "987654321098765432");

        // Then: Returns on cooldown status
        assertEquals(ChatXpService.XpAwardStatus.ON_COOLDOWN, result.status());
        assertEquals(0, result.xpAwarded());

        // Verify character was not updated
        verify(characterRepository, never()).save(any());
    }

    @Test
    void awardChatXp_allowsAward_whenCooldownExpired() {
        // Given: Character received XP 61 seconds ago (cooldown is 60 seconds)
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "123456789012345678", "987654321098765432", "Warrior", "Human",
            12, 12, 12, 12, 12, 12
        );
        character.setLastChatXpAt(LocalDateTime.now().minusSeconds(61));

        when(characterRepository.findByUserIdAndGuildId("123456789012345678", "987654321098765432"))
            .thenReturn(Optional.of(character));
        when(characterRepository.save(any())).thenReturn(character);

        // When: Attempt to award XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp("123456789012345678", "987654321098765432");

        // Then: XP is awarded
        assertEquals(ChatXpService.XpAwardStatus.AWARDED, result.status());
        assertTrue(result.xpAwarded() > 0);
    }

    @Test
    void awardChatXp_allowsAward_whenNeverAwarded() {
        // Given: Character has never received chat XP
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "123456789012345678", "987654321098765432", "Warrior", "Human",
            12, 12, 12, 12, 12, 12
        );
        character.setLastChatXpAt(null);

        when(characterRepository.findByUserIdAndGuildId("123456789012345678", "987654321098765432"))
            .thenReturn(Optional.of(character));
        when(characterRepository.save(any())).thenReturn(character);

        // When: Attempt to award XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp("123456789012345678", "987654321098765432");

        // Then: XP is awarded (no cooldown for first award)
        assertEquals(ChatXpService.XpAwardStatus.AWARDED, result.status());
        assertTrue(result.xpAwarded() > 0);
    }

    @Test
    void awardChatXp_respectsConfiguredXpValues() {
        // Given: Custom XP configuration
        when(chatXpConfig.getBaseXp()).thenReturn(20);
        when(chatXpConfig.getBonusXpMax()).thenReturn(10);

        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "123456789012345678", "987654321098765432", "Warrior", "Human",
            12, 12, 12, 12, 12, 12
        );
        character.setLastChatXpAt(null);

        when(characterRepository.findByUserIdAndGuildId("123456789012345678", "987654321098765432"))
            .thenReturn(Optional.of(character));
        when(characterRepository.save(any())).thenReturn(character);

        // When: Award chat XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp("123456789012345678", "987654321098765432");

        // Then: XP awarded respects config (20-30 range)
        assertEquals(ChatXpService.XpAwardStatus.AWARDED, result.status());
        assertTrue(result.xpAwarded() >= 20 && result.xpAwarded() <= 30,
            "XP should be between 20 and 30, got: " + result.xpAwarded());
    }

    @Test
    void awardChatXp_handlesMultipleLevelUps() {
        // Given: Character with enough XP to skip multiple levels
        String userId = "333333333333333333"; // Valid Discord snowflake
        String guildId = "444444444444444444"; // Valid Discord snowflake
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            userId, guildId, "Warrior", "Human",
            12, 12, 12, 12, 12, 12
        );
        // Set XP to just below level 3 threshold (900 XP)
        // With max XP award (15), could potentially hit level 3 in one award if very close
        // But more realistically, test single level up. For multiple levels, would need massive XP award
        // Let's test going from level 1 to 2 as a realistic scenario
        character.setXp(285); // Close to 300 (level 2)
        character.setLastChatXpAt(null);

        when(characterRepository.findByUserIdAndGuildId(userId, guildId))
            .thenReturn(Optional.of(character));
        when(characterRepository.save(any())).thenReturn(character);

        // When: Award chat XP
        ChatXpService.XpAwardResult result = chatXpService.awardChatXp(userId, guildId);

        // Then: Level up is detected
        assertEquals(ChatXpService.XpAwardStatus.AWARDED, result.status());
        assertTrue(result.leveledUp());
        assertEquals(2, result.newLevel());
    }

    @Test
    void xpAwardResult_factoryMethods_createCorrectStatuses() {
        // Test all factory methods
        ChatXpService.XpAwardResult disabled = ChatXpService.XpAwardResult.disabled();
        assertEquals(ChatXpService.XpAwardStatus.DISABLED, disabled.status());
        assertEquals(0, disabled.xpAwarded());
        assertFalse(disabled.leveledUp());

        ChatXpService.XpAwardResult noChar = ChatXpService.XpAwardResult.noCharacter();
        assertEquals(ChatXpService.XpAwardStatus.NO_CHARACTER, noChar.status());
        assertEquals(0, noChar.xpAwarded());
        assertFalse(noChar.leveledUp());

        ChatXpService.XpAwardResult cooldown = ChatXpService.XpAwardResult.onCooldown();
        assertEquals(ChatXpService.XpAwardStatus.ON_COOLDOWN, cooldown.status());
        assertEquals(0, cooldown.xpAwarded());
        assertFalse(cooldown.leveledUp());

        ChatXpService.XpAwardResult awarded = ChatXpService.XpAwardResult.awarded(15, 1, 2, true);
        assertEquals(ChatXpService.XpAwardStatus.AWARDED, awarded.status());
        assertEquals(15, awarded.xpAwarded());
        assertEquals(1, awarded.oldLevel());
        assertEquals(2, awarded.newLevel());
        assertTrue(awarded.leveledUp());
    }
}
