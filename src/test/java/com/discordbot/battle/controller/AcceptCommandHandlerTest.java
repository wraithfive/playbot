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
        when(user.getId()).thenReturn("222222222222222222");
        when(user.getName()).thenReturn("Opponent");
        when(guild.getId()).thenReturn("333333333333333333");
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        when(replyAction.addComponents(any(ActionRow.class))).thenReturn(replyAction);
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
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
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
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("444444444444444444"); // Different guild

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
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("333333333333333333");
        when(battleService.hasCharacter("333333333333333333", "222222222222222222")).thenReturn(false);

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
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("333333333333333333");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("111111111111111111");
        when(activeBattle.getOpponentId()).thenReturn("222222222222222222");
        when(activeBattle.getChallengerHp()).thenReturn(100);
        when(activeBattle.getOpponentHp()).thenReturn(100);
        when(activeBattle.getStatus()).thenReturn(ActiveBattle.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("111111111111111111");
        when(battleService.hasCharacter("333333333333333333", "222222222222222222")).thenReturn(true);

        // When: Handle command
        handler.handle(event);

        // Then: Accepts challenge and shows battle start
        verify(battleService).acceptChallenge("battle123", "222222222222222222");

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
            .anyMatch(f -> f.getName().equals("Status") && f.getValue().equals("ACTIVE")));

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
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("333333333333333333");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("111111111111111111");
        when(activeBattle.getOpponentId()).thenReturn("222222222222222222");
        when(activeBattle.getChallengerHp()).thenReturn(85);
        when(activeBattle.getOpponentHp()).thenReturn(92);
        when(activeBattle.getStatus()).thenReturn(ActiveBattle.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("222222222222222222");
        when(battleService.hasCharacter("333333333333333333", "222222222222222222")).thenReturn(true);

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
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("333333333333333333");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("111111111111111111");
        when(activeBattle.getOpponentId()).thenReturn("222222222222222222");
        when(activeBattle.getChallengerHp()).thenReturn(100);
        when(activeBattle.getOpponentHp()).thenReturn(100);
        when(activeBattle.getStatus()).thenReturn(ActiveBattle.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("111111111111111111");
        when(battleService.hasCharacter("333333333333333333", "222222222222222222")).thenReturn(true);

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
    void handle_addsActionButtons() {
        // Given: Valid battle
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("333333333333333333");
        when(activeBattle.getId()).thenReturn("battle456");
        when(activeBattle.getChallengerId()).thenReturn("111111111111111111");
        when(activeBattle.getOpponentId()).thenReturn("222222222222222222");
        when(activeBattle.getChallengerHp()).thenReturn(100);
        when(activeBattle.getOpponentHp()).thenReturn(100);
        when(activeBattle.getStatus()).thenReturn(ActiveBattle.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("111111111111111111");
        when(battleService.hasCharacter("333333333333333333", "222222222222222222")).thenReturn(true);

        // When: Handle command
        handler.handle(event);

        // Then: Action row with 3 buttons is added
        ArgumentCaptor<ActionRow> actionRowCaptor = ArgumentCaptor.forClass(ActionRow.class);
        verify(replyAction).addComponents(actionRowCaptor.capture());

        ActionRow actionRow = actionRowCaptor.getValue();
        assertEquals(3, actionRow.getButtons().size(), "Should have 3 action buttons");
    }

    @Test
    void handle_handlesIllegalStateException() {
        // Given: Battle can't be accepted (business logic error)
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("333333333333333333");
        when(activeBattle.getId()).thenReturn("battle123");
        when(battleService.hasCharacter("333333333333333333", "222222222222222222")).thenReturn(true);
        when(battleService.acceptChallenge("battle123", "222222222222222222"))
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
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("333333333333333333");
        when(activeBattle.getId()).thenReturn("battle123");
        when(battleService.hasCharacter("333333333333333333", "222222222222222222")).thenReturn(true);
        when(battleService.acceptChallenge("battle123", "222222222222222222"))
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
        when(battleService.findPendingBattleForOpponent("222222222222222222"))
            .thenReturn(Optional.of(activeBattle));
        when(activeBattle.getGuildId()).thenReturn("333333333333333333");
        when(activeBattle.getId()).thenReturn("battle123");
        when(activeBattle.getChallengerId()).thenReturn("111111111111111111");
        when(activeBattle.getOpponentId()).thenReturn("222222222222222222");
        when(activeBattle.getChallengerHp()).thenReturn(100);
        when(activeBattle.getOpponentHp()).thenReturn(100);
        when(activeBattle.getStatus()).thenReturn(ActiveBattle.BattleStatus.ACTIVE);
        when(activeBattle.getCurrentTurnUserId()).thenReturn("111111111111111111");
        when(battleService.hasCharacter("333333333333333333", "222222222222222222")).thenReturn(true);

        // When: Handle command
        handler.handle(event);

        // Then: Full acceptance flow executes
        verify(battleService).findPendingBattleForOpponent("222222222222222222");
        verify(battleService).hasCharacter("333333333333333333", "222222222222222222");
        verify(battleService).acceptChallenge("battle123", "222222222222222222");
    }
}
