package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.entity.PlayerCharacterTestFactory;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CharacterCommandHandler.
 * Tests the /character command for viewing character sheets.
 */
class CharacterCommandHandlerTest {

    @Mock
    private BattleProperties battleProperties;

    @Mock
    private BattleProperties.ProgressionConfig progressionConfig;

    @Mock
    private BattleProperties.ProgressionConfig.XpConfig xpConfig;

    @Mock
    private PlayerCharacterRepository characterRepository;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private User commandUser;

    @Mock
    private User targetUser;

    @Mock
    private Member member;

    @Mock
    private JDA jda;

    @Mock
    private RestAction<User> userRestAction;

    @Mock
    private ReplyCallbackAction replyAction;

    private CharacterCommandHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(battleProperties.isEnabled()).thenReturn(true);
        when(battleProperties.getProgression()).thenReturn(progressionConfig);
        when(progressionConfig.getXp()).thenReturn(xpConfig);
        when(xpConfig.getBaseHp()).thenReturn(20);
        when(xpConfig.getHpPerLevel()).thenReturn(5);

        handler = new CharacterCommandHandler(battleProperties, characterRepository);

        // Default mocks
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(commandUser);
        when(event.getJDA()).thenReturn(jda);
        when(commandUser.getId()).thenReturn("user1");
        when(guild.getId()).thenReturn("guild1");
        when(guild.getName()).thenReturn("Test Server");
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        when(replyAction.queue()).thenReturn(null);
    }

    @Test
    void canHandle_returnsTrueForCharacterWhenEnabled() {
        assertTrue(handler.canHandle("character"));
    }

    @Test
    void canHandle_returnsFalseWhenBattleSystemDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);
        assertFalse(handler.canHandle("character"));
    }

    @Test
    void canHandle_returnsFalseForOtherCommands() {
        assertFalse(handler.canHandle("duel"));
        assertFalse(handler.canHandle("leaderboard"));
        assertFalse(handler.canHandle("other"));
    }

    @Test
    void handle_rejectsDirectMessages() {
        // Given: No guild (DM)
        when(event.getGuild()).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with error
        verify(event).reply("❌ This command must be used in a server (guild).");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_viewsOwnCharacter_whenNoUserOptionProvided() {
        // Given: User has character, no target user specified
        PlayerCharacter character = createTestCharacter("user1");
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user1")).thenReturn(member);
        when(member.getEffectiveName()).thenReturn("TestUser");

        // When: Handle command
        handler.handle(event);

        // Then: Displays own character
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("🧙 Character Sheet — TestUser", embed.getTitle());
        assertTrue(embed.getDescription().contains("Warrior"));
        assertTrue(embed.getDescription().contains("Human"));
    }

    @Test
    void handle_viewsOtherUsersCharacter_whenUserOptionProvided() {
        // Given: Target user has character
        PlayerCharacter character = createTestCharacter("user2");
        OptionMapping userOption = mock(OptionMapping.class);
        when(userOption.getAsUser()).thenReturn(targetUser);
        when(targetUser.getId()).thenReturn("user2");
        when(event.getOption("user")).thenReturn(userOption);
        when(characterRepository.findByUserIdAndGuildId("user2", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user2")).thenReturn(member);
        when(member.getEffectiveName()).thenReturn("TargetUser");

        // When: Handle command
        handler.handle(event);

        // Then: Displays target user's character
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("🧙 Character Sheet — TargetUser", embed.getTitle());
    }

    @Test
    void handle_handlesNoCharacter_forSelf() {
        // Given: User doesn't have character
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.empty());

        // When: Handle command
        handler.handle(event);

        // Then: Shows helpful message for self
        verify(event).reply("❌ You don't have a character yet. Use /create-character to make one!");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_handlesNoCharacter_forOtherUser() {
        // Given: Target user doesn't have character
        OptionMapping userOption = mock(OptionMapping.class);
        when(userOption.getAsUser()).thenReturn(targetUser);
        when(targetUser.getId()).thenReturn("user2");
        when(targetUser.getName()).thenReturn("SomeUser");
        when(event.getOption("user")).thenReturn(userOption);
        when(characterRepository.findByUserIdAndGuildId("user2", "guild1"))
            .thenReturn(Optional.empty());

        // When: Handle command
        handler.handle(event);

        // Then: Shows message about other user
        verify(event).reply("❌ SomeUser doesn't have a character in this server.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_resolvesDisplayName_fromMemberInCache() {
        // Given: Member is in cache
        PlayerCharacter character = createTestCharacter("user1");
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user1")).thenReturn(member);
        when(member.getEffectiveName()).thenReturn("NicknameUser");

        // When: Handle command
        handler.handle(event);

        // Then: Uses member's effective name (server nickname)
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("🧙 Character Sheet — NicknameUser", embed.getTitle());
        verify(jda, never()).retrieveUserById(anyString()); // Should not fetch from API
    }

    @Test
    void handle_resolvesDisplayName_fromUserApi_whenMemberNotInCache() {
        // Given: Member not in cache, fetch from API
        PlayerCharacter character = createTestCharacter("user1");
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user1")).thenReturn(null); // Not in cache
        when(jda.retrieveUserById("user1")).thenReturn(userRestAction);
        when(userRestAction.complete()).thenReturn(targetUser);
        when(targetUser.getName()).thenReturn("ApiUser");

        // When: Handle command
        handler.handle(event);

        // Then: Fetches user from API
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("🧙 Character Sheet — ApiUser", embed.getTitle());
        verify(jda).retrieveUserById("user1");
    }

    @Test
    void handle_resolvesDisplayName_fallsBackToUnknownUser_onApiError() {
        // Given: Member not in cache, API fetch fails
        PlayerCharacter character = createTestCharacter("user1");
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user1")).thenReturn(null); // Not in cache
        when(jda.retrieveUserById("user1")).thenReturn(userRestAction);
        when(userRestAction.complete()).thenThrow(new RuntimeException("API error"));

        // When: Handle command
        handler.handle(event);

        // Then: Falls back to "Unknown User"
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("🧙 Character Sheet — Unknown User", embed.getTitle());
    }

    @Test
    void handle_displaysDerivedStats_hpAndAc() {
        // Given: Character with specific stats (CON 14, DEX 16)
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "user1", "guild1", "Warrior", "Human",
            12, 16, 14, 10, 10, 10 // DEX 16, CON 14
        );
        character.setLevel(3); // Level 3
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user1")).thenReturn(member);
        when(member.getEffectiveName()).thenReturn("TestUser");

        // When: Handle command
        handler.handle(event);

        // Then: HP and AC are displayed
        // HP = baseHp + (conMod * level) + (hpPerLevel * (level-1))
        // HP = 20 + (2 * 3) + (5 * 2) = 20 + 6 + 10 = 36
        // AC = 10 + dexMod = 10 + 3 = 13
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertTrue(embed.getDescription().contains("HP: **36**"),
            "Should show HP 36, got: " + embed.getDescription());
        assertTrue(embed.getDescription().contains("AC: **13**"),
            "Should show AC 13, got: " + embed.getDescription());
    }

    @Test
    void handle_displaysAbilityScores_withPositiveModifiers() {
        // Given: Character with high stats (18 STR = +4, 16 DEX = +3)
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "user1", "guild1", "Warrior", "Human",
            18, 16, 14, 12, 10, 8
        );
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user1")).thenReturn(member);
        when(member.getEffectiveName()).thenReturn("TestUser");

        // When: Handle command
        handler.handle(event);

        // Then: Ability scores shown with positive modifiers
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field strField = embed.getFields().get(0);
        MessageEmbed.Field dexField = embed.getFields().get(1);

        assertEquals("Strength", strField.getName());
        assertEquals("18 (+4)", strField.getValue());
        assertEquals("Dexterity", dexField.getName());
        assertEquals("16 (+3)", dexField.getValue());
    }

    @Test
    void handle_displaysAbilityScores_withNegativeModifiers() {
        // Given: Character with low stats (6 STR = -2, 8 DEX = -1)
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "user1", "guild1", "Wizard", "Human",
            6, 8, 10, 16, 14, 12
        );
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user1")).thenReturn(member);
        when(member.getEffectiveName()).thenReturn("TestUser");

        // When: Handle command
        handler.handle(event);

        // Then: Ability scores shown with negative modifiers
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field strField = embed.getFields().get(0);
        MessageEmbed.Field dexField = embed.getFields().get(1);

        assertEquals("6 (-2)", strField.getValue());
        assertEquals("8 (-1)", dexField.getValue());
    }

    @Test
    void handle_displaysAbilityScores_withZeroModifiers() {
        // Given: Character with average stats (10-11 = +0)
        PlayerCharacter character = PlayerCharacterTestFactory.create(
            "user1", "guild1", "Warrior", "Human",
            10, 11, 10, 11, 10, 11
        );
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user1")).thenReturn(member);
        when(member.getEffectiveName()).thenReturn("TestUser");

        // When: Handle command
        handler.handle(event);

        // Then: Ability scores shown with +0 modifiers
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field strField = embed.getFields().get(0);
        MessageEmbed.Field dexField = embed.getFields().get(1);

        assertEquals("10 (+0)", strField.getValue());
        assertEquals("11 (+0)", dexField.getValue());
    }

    @Test
    void handle_displaysFooterWithCreationDate() {
        // Given: Character created at specific time
        PlayerCharacter character = createTestCharacter("user1");
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(character));
        when(guild.getMemberById("user1")).thenReturn(member);
        when(member.getEffectiveName()).thenReturn("TestUser");

        // When: Handle command
        handler.handle(event);

        // Then: Footer shows creation date
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed.getFooter());
        assertTrue(embed.getFooter().getText().startsWith("Created "),
            "Footer should start with 'Created ', got: " + embed.getFooter().getText());
        assertTrue(embed.getFooter().getText().matches("Created \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"),
            "Footer should match 'Created YYYY-MM-DD HH:MM' format");
    }

    @Test
    void handle_handlesExceptions() {
        // Given: Repository throws exception
        when(event.getOption("user")).thenReturn(null);
        when(characterRepository.findByUserIdAndGuildId(any(), any()))
            .thenThrow(new RuntimeException("Database error"));

        // When: Handle command
        handler.handle(event);

        // Then: Returns error message
        verify(event).reply("❌ Failed to show character.");
        verify(replyAction).setEphemeral(true);
    }

    /**
     * Helper: Create test character with default stats
     */
    private PlayerCharacter createTestCharacter(String userId) {
        return PlayerCharacterTestFactory.create(
            userId, "guild1", "Warrior", "Human",
            12, 12, 12, 12, 12, 12
        );
    }
}
