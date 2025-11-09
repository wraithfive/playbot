package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for /create-character command handler (modal-based flow).
 */
class CreateCharacterCommandHandlerTest {

    private CreateCharacterCommandHandler handler;
    private PlayerCharacterRepository repository;
    private BattleProperties battleProperties;
    private SlashCommandInteractionEvent event;
    private ModalCallbackAction modalAction;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        battleProperties = new BattleProperties();
        battleProperties.setEnabled(true);
        
        repository = mock(PlayerCharacterRepository.class);
        handler = new CreateCharacterCommandHandler(battleProperties, repository);
        
        event = mock(SlashCommandInteractionEvent.class);
        modalAction = mock(ModalCallbackAction.class);
        replyAction = mock(ReplyCallbackAction.class);
        
        // Mock user and guild
        User user = mock(User.class);
        Guild guild = mock(Guild.class);
        when(event.getUser()).thenReturn(user);
        when(event.getGuild()).thenReturn(guild);
        when(user.getId()).thenReturn("123456789");
        when(guild.getId()).thenReturn("987654321");
        
        // Mock repository to return empty (no existing character) by default
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        
        when(event.replyModal(any(Modal.class))).thenReturn(modalAction);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    }


    @Test
    void canHandle_whenBattleEnabledAndCreateCharacter_returnsTrue() {
        assertTrue(handler.canHandle("create-character"));
    }

    @Test
    void canHandle_whenBattleDisabled_returnsFalse() {
        battleProperties.setEnabled(false);
        assertFalse(handler.canHandle("create-character"));
    }

    @Test
    void canHandle_whenDifferentCommand_returnsFalse() {
        assertFalse(handler.canHandle("battle-help"));
    }

    @Test
    void handle_noExistingCharacter_displaysModal() {
        // Act
        handler.handle(event);

        // Assert - verify modal is displayed
        ArgumentCaptor<Modal> modalCaptor = ArgumentCaptor.forClass(Modal.class);
        verify(event).replyModal(modalCaptor.capture());
        
        Modal modal = modalCaptor.getValue();
        assertNotNull(modal);
        assertTrue(modal.getId().startsWith("char-create:"));
        assertTrue(modal.getId().contains("123456789")); // Contains user ID
        assertEquals("⚔️ Create Your Character", modal.getTitle());
    }

    @Test
    void handle_characterAlreadyExists_returnsError() {
        // Arrange
        PlayerCharacter existing = new PlayerCharacter("123456789", "987654321", 
            "Warrior", "Human", 15, 14, 13, 12, 10, 8);
        when(repository.findByUserIdAndGuildId("123456789", "987654321"))
            .thenReturn(Optional.of(existing));

        // Act
        handler.handle(event);

        // Assert - verify error message, no modal
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        verify(event, never()).replyModal(any(Modal.class));

        String message = messageCaptor.getValue();
        assertTrue(message.contains("already have a character"));
    }

    @Test
    void handle_exceptionOccurs_returnsErrorMessage() {
        // Arrange - force an exception
        when(repository.findByUserIdAndGuildId(anyString(), anyString()))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        handler.handle(event);

        // Assert - verify error reply
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        verify(event, never()).replyModal(any(Modal.class));

        String message = messageCaptor.getValue();
        assertTrue(message.contains("error"));
    }
}
