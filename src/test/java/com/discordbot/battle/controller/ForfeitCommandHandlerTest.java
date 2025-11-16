package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattleService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ForfeitCommandHandler.
 * Tests the /forfeit command for battle forfeit.
 */
class ForfeitCommandHandlerTest {

    @Mock
    private BattleProperties battleProperties;

    @Mock
    private BattleService battleService;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private User user;

    @Mock
    private ReplyCallbackAction replyAction;

    @Mock
    private ActiveBattle activeBattle;

    private ForfeitCommandHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(battleProperties.isEnabled()).thenReturn(true);
        handler = new ForfeitCommandHandler(battleProperties, battleService);

        // Default mocks
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user1");
        when(user.getName()).thenReturn("Player");
        when(guild.getId()).thenReturn("guild1");
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        when(replyAction.queue()).thenReturn(null);
    }

    @Test
    void canHandle_returnsTrueForForfeitWhenEnabled() {
        assertTrue(handler.canHandle("forfeit"));
    }

    @Test
    void canHandle_returnsFalseWhenBattleSystemDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);
        assertFalse(handler.canHandle("forfeit"));
    }

    @Test
    void canHandle_returnsFalseForOtherCommands() {
        assertFalse(handler.canHandle("duel"));
        assertFalse(handler.canHandle("accept"));
        assertFalse(handler.canHandle("other"));
    }

    @Test
    void handle_rejectsDirectMessages() {
        // Given: No guild (DM)
        when(event.getGuild()).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with error
        verify(event).reply("❌ This command can only be used in a server.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).findActiveBattleForUser(any());
    }

    @Test
    void handle_rejectsWhenNoActiveBattle() {
        // Given: No active battle for user
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.empty());

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with helpful message
        verify(event).reply("❌ You don't have any active battles.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).forfeit(any(), any());
    }

    @Test
    void handle_rejectsWhenBattleInDifferentGuild() {
        // Given: Active battle exists but in different guild
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild2"); // Different guild

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with guild mismatch message
        verify(event).reply("❌ Your active battle is in a different server.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).forfeit(any(), any());
    }

    @Test
    void handle_forfeitsBattle_whenUserIsChallenger_opponentWins() {
        // Given: User is the challenger (forfeits to opponent)
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");

        // When: Handle command
        handler.handle(event);

        // Then: Forfeits battle and opponent wins
        verify(battleService).forfeit("battle123", "user1");

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("🏳️ Battle Forfeited", embed.getTitle());
        assertTrue(embed.getDescription().contains("has forfeited the battle"));

        // Winner should be user2 (opponent)
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Winner") && f.getValue().contains("user2")));

        // Should have battle ID
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Battle ID") && f.getValue().equals("battle123")));

        // Should have encouraging footer
        assertNotNull(embed.getFooter());
        assertTrue(embed.getFooter().getText().contains("Better luck next time"));
    }

    @Test
    void handle_forfeitsBattle_whenUserIsOpponent_challengerWins() {
        // Given: User is the opponent (forfeits to challenger)
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle456");
        when(activeBattle.getChallengerId()).thenReturn("user3");
        when(activeBattle.getOpponentId()).thenReturn("user1");

        // When: Handle command
        handler.handle(event);

        // Then: Forfeits battle and challenger wins
        verify(battleService).forfeit("battle456", "user1");

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();

        // Winner should be user3 (challenger)
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Winner") && f.getValue().contains("user3")));
    }

    @Test
    void handle_displaysBattleIdInEmbed() {
        // Given: Valid forfeit
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle-xyz-789");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");

        // When: Handle command
        handler.handle(event);

        // Then: Battle ID is displayed
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Battle ID") && f.getValue().equals("battle-xyz-789")));
    }

    @Test
    void handle_displaysEncouragingFooter() {
        // Given: Valid forfeit
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");

        // When: Handle command
        handler.handle(event);

        // Then: Footer has encouraging message
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed.getFooter());
        assertEquals("Better luck next time!", embed.getFooter().getText());
    }

    @Test
    void handle_handlesIllegalStateException() {
        // Given: Battle can't be forfeited (business logic error)
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");
        when(battleService.forfeit("battle123", "user1"))
            .thenThrow(new IllegalStateException("Battle is already completed"));

        // When: Handle command
        handler.handle(event);

        // Then: Shows business logic error message
        verify(event).reply("❌ Battle is already completed");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_handlesGenericException() {
        // Given: Service throws unexpected exception
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");
        when(battleService.forfeit("battle123", "user1"))
            .thenThrow(new RuntimeException("Database error"));

        // When: Handle command
        handler.handle(event);

        // Then: Shows generic error message
        verify(event).reply("❌ Failed to forfeit battle.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_verifiesFullForfeitFlow() {
        // Given: Valid active battle
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");

        // When: Handle command
        handler.handle(event);

        // Then: Full forfeit flow executes
        verify(battleService).findActiveBattleForUser("user1");
        verify(battleService).forfeit("battle123", "user1");
    }

    @Test
    void handle_embedHasRedColor() {
        // Given: Valid forfeit
        when(battleService.findActiveBattleForUser("user1"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");

        // When: Handle command
        handler.handle(event);

        // Then: Embed has red color (forfeit theme)
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed.getColor());
        // Red color should have red component > 200
        assertTrue(embed.getColor().getRed() > 200,
            "Forfeit embed should have red-ish color");
    }
}
