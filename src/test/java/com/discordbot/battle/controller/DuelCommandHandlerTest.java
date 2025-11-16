package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattleService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DuelCommandHandler.
 * Tests the /duel command for battle challenge creation.
 */
class DuelCommandHandlerTest {

    @Mock
    private BattleProperties battleProperties;

    @Mock
    private BattleService battleService;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private User challenger;

    @Mock
    private User opponent;

    @Mock
    private OptionMapping opponentOption;

    @Mock
    private ReplyCallbackAction replyAction;

    @Mock
    private ActiveBattle activeBattle;

    private DuelCommandHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(battleProperties.isEnabled()).thenReturn(true);
        handler = new DuelCommandHandler(battleProperties, battleService);

        // Default mocks
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(challenger);
        when(guild.getId()).thenReturn("guild1");
        when(challenger.getId()).thenReturn("user1");
        when(challenger.getName()).thenReturn("Challenger");
        when(opponent.getId()).thenReturn("user2");
        when(opponent.getName()).thenReturn("Opponent");
        when(opponent.isBot()).thenReturn(false);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        when(replyAction.addComponents(any(ActionRow.class))).thenReturn(replyAction);
    }

    @Test
    void canHandle_returnsTrueForDuelWhenEnabled() {
        assertTrue(handler.canHandle("duel"));
    }

    @Test
    void canHandle_returnsFalseWhenBattleSystemDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);
        assertFalse(handler.canHandle("duel"));
    }

    @Test
    void canHandle_returnsFalseForOtherCommands() {
        assertFalse(handler.canHandle("character"));
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
        verify(event).reply("❌ This command can only be used in a server.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).createChallenge(any(), any(), any());
    }

    @Test
    void handle_rejectsMissingOpponent() {
        // Given: No opponent option provided
        when(event.getOption("opponent")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with error
        verify(event).reply("❌ You must select an opponent.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).createChallenge(any(), any(), any());
    }

    @Test
    void handle_rejectsSelfDuel() {
        // Given: Opponent is the challenger
        when(opponentOption.getAsUser()).thenReturn(challenger);
        when(event.getOption("opponent")).thenReturn(opponentOption);

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with error
        verify(event).reply("❌ You cannot duel yourself.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).createChallenge(any(), any(), any());
    }

    @Test
    void handle_rejectsBotOpponent() {
        // Given: Opponent is a bot
        when(opponent.isBot()).thenReturn(true);
        when(opponentOption.getAsUser()).thenReturn(opponent);
        when(event.getOption("opponent")).thenReturn(opponentOption);

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with error
        verify(event).reply("❌ You cannot challenge bots.");
        verify(replyAction).setEphemeral(true);
        verify(battleService, never()).createChallenge(any(), any(), any());
    }

    @Test
    void handle_createsChallenge_whenOpponentHasCharacter() {
        // Given: Valid duel request, opponent has character
        when(opponentOption.getAsUser()).thenReturn(opponent);
        when(event.getOption("opponent")).thenReturn(opponentOption);
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);
        when(activeBattle.getId()).thenReturn("battle123");
        when(battleService.createChallenge("guild1", "user1", "user2"))
            .thenReturn(activeBattle);

        // When: Handle command
        handler.handle(event);

        // Then: Creates challenge and shows Accept/Decline buttons
        verify(battleService).createChallenge("guild1", "user1", "user2");

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        ArgumentCaptor<ActionRow> actionRowCaptor = ArgumentCaptor.forClass(ActionRow.class);
        verify(event).replyEmbeds(embedCaptor.capture());
        verify(replyAction).addComponents(actionRowCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⚔️ Duel Challenge", embed.getTitle());
        assertTrue(embed.getDescription().contains("has challenged"));
        assertTrue(embed.getDescription().contains("Accept"));
        assertFalse(embed.getDescription().contains("does not have a character yet"),
            "Should not show character warning when opponent has character");

        // Should have battle ID field
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Battle ID") && f.getValue().equals("battle123")));

        // Should have 2 buttons: Accept and Decline
        ActionRow actionRow = actionRowCaptor.getValue();
        List<Button> buttons = actionRow.getButtons();
        assertEquals(2, buttons.size(), "Should have 2 buttons when opponent has character");
        assertEquals("Accept", buttons.get(0).getLabel());
        assertEquals("Decline", buttons.get(1).getLabel());
    }

    @Test
    void handle_createsChallenge_whenOpponentMissingCharacter() {
        // Given: Valid duel request, opponent doesn't have character
        when(opponentOption.getAsUser()).thenReturn(opponent);
        when(event.getOption("opponent")).thenReturn(opponentOption);
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(false);
        when(activeBattle.getId()).thenReturn("battle456");
        when(battleService.createChallenge("guild1", "user1", "user2"))
            .thenReturn(activeBattle);

        // When: Handle command
        handler.handle(event);

        // Then: Creates challenge and shows Accept/Decline/Create Character buttons
        verify(battleService).createChallenge("guild1", "user1", "user2");

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        ArgumentCaptor<ActionRow> actionRowCaptor = ArgumentCaptor.forClass(ActionRow.class);
        verify(event).replyEmbeds(embedCaptor.capture());
        verify(replyAction).addComponents(actionRowCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertTrue(embed.getDescription().contains("does not have a character yet"),
            "Should show character warning when opponent missing character");
        assertTrue(embed.getDescription().contains("/create-character"));

        // Should have "Opponent Character: Not created" field
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Opponent Character") && f.getValue().equals("Not created")));

        // Should have 3 buttons: Accept, Decline, and Create Character
        ActionRow actionRow = actionRowCaptor.getValue();
        List<Button> buttons = actionRow.getButtons();
        assertEquals(3, buttons.size(), "Should have 3 buttons when opponent missing character");
        assertEquals("Accept", buttons.get(0).getLabel());
        assertEquals("Decline", buttons.get(1).getLabel());
        assertEquals("Create Character", buttons.get(2).getLabel());
    }

    @Test
    void handle_displaysBattleIdInEmbed() {
        // Given: Valid duel request
        when(opponentOption.getAsUser()).thenReturn(opponent);
        when(event.getOption("opponent")).thenReturn(opponentOption);
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);
        when(activeBattle.getId()).thenReturn("battle-xyz-789");
        when(battleService.createChallenge("guild1", "user1", "user2"))
            .thenReturn(activeBattle);

        // When: Handle command
        handler.handle(event);

        // Then: Battle ID is displayed in embed
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().equals("Battle ID") && f.getValue().equals("battle-xyz-789")));
    }

    @Test
    void handle_includesFooterWithHelpCommand() {
        // Given: Valid duel request
        when(opponentOption.getAsUser()).thenReturn(opponent);
        when(event.getOption("opponent")).thenReturn(opponentOption);
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);
        when(activeBattle.getId()).thenReturn("battle123");
        when(battleService.createChallenge("guild1", "user1", "user2"))
            .thenReturn(activeBattle);

        // When: Handle command
        handler.handle(event);

        // Then: Footer references /battle-help
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed.getFooter());
        assertTrue(embed.getFooter().getText().contains("/battle-help"));
    }

    @Test
    void handle_handlesIllegalStateException() {
        // Given: Challenger already in battle (business logic error)
        when(opponentOption.getAsUser()).thenReturn(opponent);
        when(event.getOption("opponent")).thenReturn(opponentOption);
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);
        when(battleService.createChallenge("guild1", "user1", "user2"))
            .thenThrow(new IllegalStateException("You are already in an active battle"));

        // When: Handle command
        handler.handle(event);

        // Then: Shows business logic error message
        verify(event).reply("❌ You are already in an active battle");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_handlesGenericException() {
        // Given: Service throws unexpected exception
        when(opponentOption.getAsUser()).thenReturn(opponent);
        when(event.getOption("opponent")).thenReturn(opponentOption);
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);
        when(battleService.createChallenge("guild1", "user1", "user2"))
            .thenThrow(new RuntimeException("Database error"));

        // When: Handle command
        handler.handle(event);

        // Then: Shows generic error message
        verify(event).reply("❌ Failed to create duel challenge.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_addsAcceptAndDeclineButtons() {
        // Given: Valid duel request
        when(opponentOption.getAsUser()).thenReturn(opponent);
        when(event.getOption("opponent")).thenReturn(opponentOption);
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);
        when(activeBattle.getId()).thenReturn("battle123");
        when(battleService.createChallenge("guild1", "user1", "user2"))
            .thenReturn(activeBattle);

        // When: Handle command
        handler.handle(event);

        // Then: Action row with 2 buttons is added
        ArgumentCaptor<ActionRow> actionRowCaptor = ArgumentCaptor.forClass(ActionRow.class);
        verify(replyAction).addComponents(actionRowCaptor.capture());

        ActionRow actionRow = actionRowCaptor.getValue();
        assertEquals(2, actionRow.getButtons().size(), "Should have Accept and Decline buttons");
    }

    @Test
    void handle_verifiesBothCharacterCheckAndChallengeCreation() {
        // Given: Valid duel request
        when(opponentOption.getAsUser()).thenReturn(opponent);
        when(event.getOption("opponent")).thenReturn(opponentOption);
        when(battleService.hasCharacter("guild1", "user2")).thenReturn(true);
        when(activeBattle.getId()).thenReturn("battle123");
        when(battleService.createChallenge("guild1", "user1", "user2"))
            .thenReturn(activeBattle);

        // When: Handle command
        handler.handle(event);

        // Then: Checks opponent character before creating challenge
        verify(battleService).hasCharacter("guild1", "user2");
        verify(battleService).createChallenge("guild1", "user1", "user2");
    }
}
