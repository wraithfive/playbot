package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattleService;
import com.discordbot.battle.service.StatusEffectService;
import com.discordbot.battle.util.CharacterCreationUIBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for BattleInteractionHandler.
 * Tests the primary user interface for battle interactions.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BattleInteractionHandlerTest {

    private BattleInteractionHandler handler;

    @Mock
    private BattleService battleService;

    @Mock
    private BattleProperties battleProperties;

    @Mock
    private CharacterCreationUIBuilder uiBuilder;

    @Mock
    private StatusEffectService statusEffectService;

    @Mock
    private MessageCreateData mockMessageData;

    @Mock
    private ButtonInteractionEvent event;

    @Mock
    private User user;

    @Mock
    private Guild guild;

    @Mock
    private ReplyCallbackAction replyAction;

    @Mock
    private MessageEditCallbackAction editAction;

    @Mock
    private ActiveBattle battle;

    @BeforeEach
    void setUp() {
        handler = new BattleInteractionHandler(battleService, battleProperties, uiBuilder, statusEffectService);

        // Default mock behavior
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getUser()).thenReturn(user);
        when(event.getGuild()).thenReturn(guild);
        when(user.getId()).thenReturn("123456789012345678");
        when(guild.getId()).thenReturn("987654321098765432");

        // Mock reply/edit actions
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.reply(any(MessageCreateData.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        // Use doAnswer().when() with explicit array matchers to handle varargs overloads
        doAnswer(inv -> editAction).when(event).editMessageEmbeds(any(MessageEmbed[].class));
        doAnswer(inv -> editAction).when(editAction).setComponents(any(MessageTopLevelComponent[].class));
        doAnswer(inv -> editAction).when(editAction).setComponents();

        // Mock battle
        when(battle.getId()).thenReturn("battle-123");
        when(battle.getChallengerId()).thenReturn("123456789012345678");
        when(battle.getOpponentId()).thenReturn("876543210987654321");
        when(battle.getGuildId()).thenReturn("987654321098765432");
        when(battle.getChallengerHp()).thenReturn(50);
        when(battle.getOpponentHp()).thenReturn(50);
        when(battle.getStatus()).thenReturn(ActiveBattle.BattleStatus.ACTIVE);
        when(battle.getLogEntries()).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Should ignore interactions when battle system is disabled")
    void testFeatureFlagDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);
        when(event.getComponentId()).thenReturn("battle:battle-123:attack");

        handler.onButtonInteraction(event);

        // Should return early without processing
        verify(event, never()).reply(anyString());
        verify(battleService, never()).getBattleOrThrow(anyString());
    }

    @Test
    @DisplayName("Should ignore non-battle component interactions")
    void testNonBattleComponent() {
        when(event.getComponentId()).thenReturn("other:component:id");

        handler.onButtonInteraction(event);

        // Should return early
        verify(event, never()).reply(anyString());
        verify(battleService, never()).getBattleOrThrow(anyString());
    }

    @Test
    @DisplayName("Should reject component ID with invalid format (wrong number of parts)")
    void testInvalidComponentIdFormat() {
        when(event.getComponentId()).thenReturn("battle:battle-123"); // Only 2 parts instead of 3

        handler.onButtonInteraction(event);

        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
        verify(event).reply("❌ Invalid battle component.");
    }

    @Test
    @DisplayName("Should reject component ID with excessively long battleId")
    void testInvalidBattleIdLength() {
        // BattleId longer than 50 characters
        String longBattleId = "a".repeat(60);
        when(event.getComponentId()).thenReturn("battle:" + longBattleId + ":attack");

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Invalid battle identifier.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Should reject component ID with excessively long action")
    void testInvalidActionLength() {
        // Action longer than 20 characters
        String longAction = "a".repeat(30);
        when(event.getComponentId()).thenReturn("battle:battle-123:" + longAction);

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Invalid action type.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Should reject empty battleId")
    void testEmptyBattleId() {
        when(event.getComponentId()).thenReturn("battle::attack");

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Invalid battle identifier.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    @DisplayName("Should reject empty action")
    void testEmptyAction() {
        when(event.getComponentId()).thenReturn("battle:battle-123:");

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Invalid battle component.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    @DisplayName("Should handle battle not found")
    void testBattleNotFound() {
        when(event.getComponentId()).thenReturn("battle:battle-123:attack");
        when(battleService.getBattleOrThrow("battle-123")).thenThrow(new IllegalStateException("Battle not found"));

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Battle not found or expired.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Should reject interaction from non-participant")
    void testNonParticipantInteraction() {
        when(event.getComponentId()).thenReturn("battle:battle-123:attack");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("999999999999999999"); // Not challenger or opponent

        handler.onButtonInteraction(event);

        verify(event).reply("❌ You're not part of this battle.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Should handle unknown action gracefully")
    void testUnknownAction() {
        when(event.getComponentId()).thenReturn("battle:battle-123:unknownAction");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("123456789012345678"); // Challenger

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Unknown action.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Should handle IllegalStateException with user message")
    void testIllegalStateExceptionHandling() {
        when(event.getComponentId()).thenReturn("battle:battle-123:attack");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(battle.isActive()).thenReturn(false);
        when(user.getId()).thenReturn("123456789012345678");

        // This will trigger IllegalStateException in handleAttack
        handler.onButtonInteraction(event);

        verify(event).reply("❌ Battle not active.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    @DisplayName("Should handle accept action for valid opponent")
    void testAcceptChallenge() {
        when(event.getComponentId()).thenReturn("battle:battle-123:accept");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("876543210987654321"); // Opponent
        when(battle.isPending()).thenReturn(true);
        when(battle.getOpponentId()).thenReturn("876543210987654321");
        when(battleService.hasCharacter(anyString(), anyString())).thenReturn(true);
        when(battle.getCurrentTurnUserId()).thenReturn("123456789012345678");
        when(statusEffectService.getEffectsDisplayString(anyString(), anyString())).thenReturn("");

        handler.onButtonInteraction(event);

        verify(battleService).acceptChallenge("battle-123", "876543210987654321");
        verify(editAction, atLeastOnce()).setComponents(any(MessageTopLevelComponent[].class));
        verify(editAction).queue();
    }

    @Test
    @DisplayName("Should reject accept if battle already started")
    void testAcceptAlreadyStarted() {
        when(event.getComponentId()).thenReturn("battle:battle-123:accept");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("876543210987654321");
        when(battle.isPending()).thenReturn(false); // Already started

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Battle already started.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).acceptChallenge(anyString(), anyString());
    }

    @Test
    @DisplayName("Should reject accept if user is not opponent")
    void testAcceptNotOpponent() {
        when(event.getComponentId()).thenReturn("battle:battle-123:accept");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("123456789012345678"); // Challenger, not opponent
        when(battle.isPending()).thenReturn(true);
        when(battle.getOpponentId()).thenReturn("876543210987654321");

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Only the challenged opponent can accept.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).acceptChallenge(anyString(), anyString());
    }

    @Test
    @DisplayName("Should reject accept if opponent has no character")
    void testAcceptNoCharacter() {
        when(event.getComponentId()).thenReturn("battle:battle-123:accept");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("876543210987654321");
        when(battle.isPending()).thenReturn(true);
        when(battle.getOpponentId()).thenReturn("876543210987654321");
        when(battleService.hasCharacter(anyString(), anyString())).thenReturn(false);

        handler.onButtonInteraction(event);

        verify(event).reply(contains("create a character first"));
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).acceptChallenge(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle decline action for valid opponent")
    void testDeclineChallenge() {
        when(event.getComponentId()).thenReturn("battle:battle-123:decline");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("876543210987654321"); // Opponent
        when(battle.isPending()).thenReturn(true);
        when(battle.getOpponentId()).thenReturn("876543210987654321");

        handler.onButtonInteraction(event);

        verify(battleService).declineChallenge("battle-123", "876543210987654321");
        verify(editAction).setComponents(); // Remove buttons
        verify(editAction).queue();
    }

    @Test
    @DisplayName("Should reject attack if not user's turn")
    void testAttackNotYourTurn() {
        when(event.getComponentId()).thenReturn("battle:battle-123:attack");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("876543210987654321"); // Opponent
        when(battle.isActive()).thenReturn(true);
        when(battle.getCurrentTurnUserId()).thenReturn("123456789012345678"); // Challenger's turn

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Not your turn.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).performAttack(anyString(), anyString());
    }

    @Test
    @DisplayName("Should reject defend if not user's turn")
    void testDefendNotYourTurn() {
        when(event.getComponentId()).thenReturn("battle:battle-123:defend");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("876543210987654321");
        when(battle.isActive()).thenReturn(true);
        when(battle.getCurrentTurnUserId()).thenReturn("123456789012345678");

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Not your turn.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).performDefend(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle forfeit from either participant")
    void testForfeit() {
        when(event.getComponentId()).thenReturn("battle:battle-123:forfeit");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("123456789012345678"); // Challenger
        when(battle.isActive()).thenReturn(true);
        when(battle.getChallengerId()).thenReturn("123456789012345678");
        when(battle.getOpponentId()).thenReturn("876543210987654321");

        handler.onButtonInteraction(event);

        verify(battleService).forfeit("battle-123", "123456789012345678");
        verify(battleService).awardProgressionRewards(
            eq("876543210987654321"), // Winner (opponent)
            eq("123456789012345678"), // Loser (challenger)
            anyString(),
            eq(false)
        );
        verify(editAction).setComponents(); // Remove buttons
        verify(editAction).queue();
    }

    @Test
    @DisplayName("Should reject forfeit if battle not active or pending")
    void testForfeitNotActive() {
        when(event.getComponentId()).thenReturn("battle:battle-123:forfeit");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("123456789012345678");
        when(battle.isActive()).thenReturn(false);
        when(battle.isPending()).thenReturn(false);

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Battle not active or already ended.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).forfeit(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle createchar action for opponent only")
    void testCreateCharacterButton() {
        when(event.getComponentId()).thenReturn("battle:battle-123:createchar");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("876543210987654321"); // Opponent
        when(battle.getOpponentId()).thenReturn("876543210987654321");
        when(battleService.hasCharacter(anyString(), anyString())).thenReturn(false);
        when(uiBuilder.buildCharacterCreationMessage(anyString(), anyBoolean())).thenReturn(mockMessageData);

        handler.onButtonInteraction(event);

        verify(uiBuilder).buildCharacterCreationMessage("876543210987654321", false);
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @DisplayName("Should reject createchar if user is challenger")
    void testCreateCharacterNotOpponent() {
        when(event.getComponentId()).thenReturn("battle:battle-123:createchar");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("123456789012345678"); // Challenger
        when(battle.getOpponentId()).thenReturn("876543210987654321");

        handler.onButtonInteraction(event);

        verify(event).reply("❌ Only the challenged opponent can create a character from this prompt.");
        verify(replyAction).setEphemeral(true);
        verify(uiBuilder, never()).buildCharacterCreationMessage(anyString(), anyBoolean());
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException with detailed logging")
    void testIllegalArgumentExceptionHandling() {
        when(event.getComponentId()).thenReturn("battle:battle-123:attack");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("123456789012345678");
        when(battle.isActive()).thenReturn(true);
        when(battle.getCurrentTurnUserId()).thenReturn("123456789012345678");
        when(battleService.performAttack(anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("Invalid ability"));

        handler.onButtonInteraction(event);

        verify(event).reply(contains("Invalid action"));
        verify(replyAction).setEphemeral(true);
    }

    @Test
    @DisplayName("Should handle unexpected Exception with logging")
    void testUnexpectedExceptionHandling() {
        when(event.getComponentId()).thenReturn("battle:battle-123:attack");
        when(battleService.getBattleOrThrow("battle-123")).thenReturn(battle);
        when(user.getId()).thenReturn("123456789012345678");
        when(battle.isActive()).thenReturn(true);
        when(battle.getCurrentTurnUserId()).thenReturn("123456789012345678");
        when(battleService.performAttack(anyString(), anyString()))
            .thenThrow(new RuntimeException("Database error"));

        handler.onButtonInteraction(event);

        verify(event).reply(contains("unexpected error"));
        verify(replyAction).setEphemeral(true);
    }

    @Test
    @DisplayName("Should validate component ID is battle component before processing")
    void testComponentIdValidation() {
        // Component ID that doesn't start with "battle:"
        when(event.getComponentId()).thenReturn("other:component:id");

        handler.onButtonInteraction(event);

        // Should return early without any interaction
        verify(event, never()).reply(anyString());
        verify(battleService, never()).getBattleOrThrow(anyString());
    }
}
