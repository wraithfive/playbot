package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.util.CharacterCreationUIBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for /create-character command handler (interactive embed flow).
 */
class CreateCharacterCommandHandlerTest {

    private CreateCharacterCommandHandler handler;
    private PlayerCharacterRepository repository;
    private BattleProperties battleProperties;
    private CharacterCreationUIBuilder uiBuilder;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        battleProperties = new BattleProperties();
        battleProperties.setEnabled(true);

        repository = mock(PlayerCharacterRepository.class);
    uiBuilder = new CharacterCreationUIBuilder(battleProperties);
    handler = new CreateCharacterCommandHandler(battleProperties, repository, uiBuilder);

        event = mock(SlashCommandInteractionEvent.class);
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

        when(event.reply(any(MessageCreateData.class))).thenReturn(replyAction);
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();
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
    void handle_noExistingCharacter_displaysInteractiveEmbed() {
        // Act
        handler.handle(event);

        // Assert - verify reply with MessageCreateData (contains embed + action rows)
        ArgumentCaptor<MessageCreateData> dataCaptor = ArgumentCaptor.forClass(MessageCreateData.class);
        verify(event).reply(dataCaptor.capture());

        MessageCreateData data = dataCaptor.getValue();
        assertNotNull(data);
        
        // Verify embed content
        var embeds = data.getEmbeds();
        assertFalse(embeds.isEmpty(), "Should have at least one embed");
        MessageEmbed embed = embeds.get(0);
        assertEquals("⚔️ Create Your Character", embed.getTitle());
        assertNotNull(embed.getDescription());
        assertTrue(embed.getDescription().contains("point-buy system"));

        // Verify action rows are present (menus + buttons)
        var components = data.getComponents();
        assertFalse(components.isEmpty(), "Should have action rows with components");

        // Verify the reply was configured properly
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
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

        // Assert - verify error message, no embed with components
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        verify(event, never()).reply(any(MessageCreateData.class));

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
        verify(event, never()).reply(any(MessageCreateData.class));

        String message = messageCaptor.getValue();
        assertTrue(message.contains("error"));
    }
}