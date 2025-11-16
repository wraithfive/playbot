package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.entity.BattleSession;
import com.discordbot.battle.service.BattleService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AcceptCommandHandler.
 * Tests the /accept command for accepting battle challenges.
 */
class AcceptCommandHandlerTest {

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

    private AcceptCommandHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(battleProperties.isEnabled()).thenReturn(true);
        handler = new AcceptCommandHandler(battleProperties, battleService);

        // Default mocks
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user2");
        when(user.getName()).thenReturn("Opponent");
        when(guild.getId()).thenReturn("guild1");
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        when(replyAction.addComponents(any(ActionRow.class))).thenReturn(replyAction);
        when(replyAction.queue()).thenReturn(null);
    }

    @Test
    void canHandle_returnsTrueForAcceptWhenEnabled() {
        assertTrue(handler.canHandle("accept"));
    }

    @Test
    void canHandle_returnsFalseWhenBattleSystemDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);
        assertFalse(handler.canHandle("accept"));
    }

    @Test
    void canHandle_returnsFalseForOtherCommands() {
        assertFalse(handler.canHandle("duel"));
        assertFalse(handler.canHandle("character"));
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
        verify(battleService, never()).findPendingBattleForOpponent(any());
    }

    @Test
    void handle_rejectsWhenNoPendingBattle() {
        // Given: No pending battle for user
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.empty());

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with helpful message
        verify(event).reply("❌ You don't have any pending battle challenges.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).acceptChallenge(any(), any());
    }

    @Test
    void handle_rejectsWhenBattleInDifferentGuild() {
        // Given: Pending battle exists but in different guild
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild2"); // Different guild

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with guild mismatch message
        verify(event).reply("❌ The pending battle is in a different server.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).acceptChallenge(any(), any());
    }

    @Test
    void handle_rejectsWhenOpponentHasNoCharacter() {
        // Given: Pending battle exists but opponent has no character
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(false);

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with character creation message
        verify(event).reply("❌ You need to create a character first with /create-character before accepting.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).acceptChallenge(any(), any());
    }

    @Test
    void handle_acceptsChallenge_andStartsBattle() {
        // Given: Valid pending battle
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");
        when(activeBattle.getChallengerHp()).thenReturn(100);
        when(activeBattle.getOpponentHp()).thenReturn(100);
        when(activeBattle.getStatus()).thenReturn(BattleSession.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("user1");
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);

        // When: Handle command
        handler.handle(event);

        // Then: Accepts challenge and shows battle start
        verify(battleService).acceptChallenge("battle123", "user2");

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        ArgumentCaptor<ActionRow> actionRowCaptor = ArgumentCaptor.forClass(ActionRow.class);
        verify(event).replyEmbeds(embedCaptor.capture());
        verify(replyAction).addComponents(actionRowCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⚔️ Battle Started", embed.getTitle());
        assertTrue(embed.getDescription().contains("vs"));

        // Should have HP fields
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Challenger HP") && f.getValue().equals("100")));
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Opponent HP") && f.getValue().equals("100")));

        // Should have status field
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Status") && f.getValue().equals("IN_PROGRESS")));

        // Should have turn field
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Turn")));

        // Should have battle ID in footer
        assertNotNull(embed.getFooter());
        assertTrue(embed.getFooter().getText().contains("battle123"));

        // Should have 3 action buttons: Attack, Defend, Forfeit
        ActionRow actionRow = actionRowCaptor.getValue();
        List<Button> buttons = actionRow.getButtons();
        assertEquals(3, buttons.size());
        assertEquals("⚔️ Attack", buttons.get(0).getLabel());
        assertEquals("🛡️ Defend", buttons.get(1).getLabel());
        assertEquals("🏳️ Forfeit", buttons.get(2).getLabel());
    }

    @Test
    void handle_displaysCorrectHpValues() {
        // Given: Battle with specific HP values
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");
        when(activeBattle.getChallengerHp()).thenReturn(85);
        when(activeBattle.getOpponentHp()).thenReturn(92);
        when(activeBattle.getStatus()).thenReturn(BattleSession.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("user2");
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);

        // When: Handle command
        handler.handle(event);

        // Then: HP values are displayed correctly
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Challenger HP") && f.getValue().equals("85")));
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Opponent HP") && f.getValue().equals("92")));
    }

    @Test
    void handle_displaysCurrentTurn() {
        // Given: Valid battle
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");
        when(activeBattle.getChallengerHp()).thenReturn(100);
        when(activeBattle.getOpponentHp()).thenReturn(100);
        when(activeBattle.getStatus()).thenReturn(BattleSession.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("user1");
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);

        // When: Handle command
        handler.handle(event);

        // Then: Current turn is displayed
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Turn") && f.getValue().contains("turn")));
    }

    @Test
    void handle_usesCorrectComponentIds() {
        // Given: Valid battle
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle456");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");
        when(activeBattle.getChallengerHp()).thenReturn(100);
        when(activeBattle.getOpponentHp()).thenReturn(100);
        when(activeBattle.getStatus()).thenReturn(BattleSession.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("user1");
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);

        // When: Handle command
        handler.handle(event);

        // Then: Component IDs contain battle ID and action
        ArgumentCaptor<ActionRow> actionRowCaptor = ArgumentCaptor.forClass(ActionRow.class);
        verify(replyAction).addComponents(actionRowCaptor.capture());

        ActionRow actionRow = actionRowCaptor.getValue();
        List<Button> buttons = actionRow.getButtons();

        assertTrue(buttons.get(0).getId().contains("battle456"),
            "Attack button ID should contain battle ID");
        assertTrue(buttons.get(0).getId().contains("attack"),
            "Attack button ID should contain 'attack'");
        assertTrue(buttons.get(1).getId().contains("defend"),
            "Defend button ID should contain 'defend'");
        assertTrue(buttons.get(2).getId().contains("forfeit"),
            "Forfeit button ID should contain 'forfeit'");
    }

    @Test
    void handle_handlesIllegalStateException() {
        // Given: Battle can't be accepted (business logic error)
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);
        when(battleService.acceptChallenge("battle123", "user2"))
            .thenThrow(new IllegalStateException("Battle is no longer pending"));

        // When: Handle command
        handler.handle(event);

        // Then: Shows business logic error message
        verify(event).reply("❌ Battle is no longer pending");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_handlesGenericException() {
        // Given: Service throws unexpected exception
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);
        when(battleService.acceptChallenge("battle123", "user2"))
            .thenThrow(new RuntimeException("Database error"));

        // When: Handle command
        handler.handle(event);

        // Then: Shows generic error message
        verify(event).reply("❌ Failed to accept battle.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_verifiesFullAcceptanceFlow() {
        // Given: Valid pending battle
        when(battleService.findPendingBattleForOpponent("user2"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("guild1");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("user1");
        when(activeBattle.getOpponentId()).thenReturn("user2");
        when(activeBattle.getChallengerHp()).thenReturn(100);
        when(activeBattle.getOpponentHp()).thenReturn(100);
        when(activeBattle.getStatus()).thenReturn(BattleSession.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("user1");
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);

        // When: Handle command
        handler.handle(event);

        // Then: Full acceptance flow executes
        verify(battleService).findPendingBattleForOpponent("user2");
        verify(battleService).hasCharacter("guild1", "user2");
        verify(battleService).acceptChallenge("battle123", "user2");
    }
}
