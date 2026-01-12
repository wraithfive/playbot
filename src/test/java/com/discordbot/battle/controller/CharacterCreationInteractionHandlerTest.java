package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.CharacterValidationService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CharacterCreationInteractionHandler (button and select menu interactions).
 */
@SuppressWarnings("null")
class CharacterCreationInteractionHandlerTest {

    private CharacterCreationInteractionHandler handler;
    private BattleProperties battleProperties;
    private PlayerCharacterRepository repository;
    private CharacterValidationService validationService;

    @BeforeEach
    void setUp() {
        battleProperties = new BattleProperties();
        battleProperties.setEnabled(true);
        // Set required class configs
        battleProperties.getClassConfig().getWarrior().setBaseHp(10);
        battleProperties.getClassConfig().getRogue().setBaseHp(8);
        battleProperties.getClassConfig().getMage().setBaseHp(6);
        battleProperties.getClassConfig().getCleric().setBaseHp(8);

        repository = mock(PlayerCharacterRepository.class);
        validationService = mock(CharacterValidationService.class);

        handler = new CharacterCreationInteractionHandler(battleProperties, repository, validationService);
    }

    // ============ String Select Interaction Tests ============

    @Test
    void onStringSelectInteraction_classSelection_updatesState() {
        // Arrange
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        MessageEditCallbackAction deferEdit = mock(MessageEditCallbackAction.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);

        when(event.getComponentId()).thenReturn("char-create:123:class");
        when(event.getUser()).thenReturn(user);
        when(event.getGuild()).thenReturn(guild);
        when(user.getId()).thenReturn("123");
        when(guild.getId()).thenReturn("456");
        when(event.getValues()).thenReturn(List.of("Warrior"));
        when(event.deferEdit()).thenReturn(deferEdit);

        // Act
        handler.onStringSelectInteraction(event);

        // Assert
        verify(event).deferEdit();
    }

    @Test
    void onStringSelectInteraction_wrongUser_sendsError() {
        // Arrange
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);
        User user = mock(User.class);

        when(event.getComponentId()).thenReturn("char-create:123:class");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("999"); // Different user
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        // Act
        handler.onStringSelectInteraction(event);

        // Assert
        verify(event).reply(contains("not for you"));
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void onStringSelectInteraction_invalidComponent_ignores() {
        // Arrange
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        when(event.getComponentId()).thenReturn("other-component:123");

        // Act
        handler.onStringSelectInteraction(event);

        // Assert
        verify(event, never()).reply(anyString());
        verify(event, never()).deferEdit();
    }

    // ============ Button Interaction Tests ============

    @Test
    void onButtonInteraction_cancelButton_removesState() {
        // Arrange
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        MessageEditCallbackAction editAction = mock(MessageEditCallbackAction.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);

        when(event.getComponentId()).thenReturn("char-create:123:cancel");
        when(event.getUser()).thenReturn(user);
        when(event.getGuild()).thenReturn(guild);
        when(user.getId()).thenReturn("123");
        when(guild.getId()).thenReturn("456");
        when(event.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
        when(editAction.setComponents()).thenReturn(editAction);
        doNothing().when(editAction).queue();

        // Act
        handler.onButtonInteraction(event);

        // Assert
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).editMessageEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed);
        assertTrue(embed.getTitle().contains("Cancelled"));
    }

    @Test
    void onButtonInteraction_submitWithoutClassAndRace_sendsError() {
        // Arrange
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);

        when(event.getComponentId()).thenReturn("char-create:123:submit");
        when(event.getUser()).thenReturn(user);
        when(event.getGuild()).thenReturn(guild);
        when(user.getId()).thenReturn("123");
        when(guild.getId()).thenReturn("456");
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        // First create state by simulating a stat button click
        ButtonInteractionEvent setupEvent = mock(ButtonInteractionEvent.class);
        MessageEditCallbackAction deferEdit = mock(MessageEditCallbackAction.class);
        when(setupEvent.getComponentId()).thenReturn("char-create:123:stat+:str");
        when(setupEvent.getUser()).thenReturn(user);
        when(setupEvent.getGuild()).thenReturn(guild);
        when(setupEvent.deferEdit()).thenReturn(deferEdit);

        // Actually trigger the setup event to create state
        handler.onButtonInteraction(setupEvent);

        // Act
        handler.onButtonInteraction(event);

        // Assert
        verify(event).reply(contains("select both a class and race"));
    }

    @Test
    void onButtonInteraction_statIncrease_updatesState() {
        // Arrange
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        MessageEditCallbackAction deferEdit = mock(MessageEditCallbackAction.class);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);

        when(event.getComponentId()).thenReturn("char-create:123:stat+:str");
        when(event.getUser()).thenReturn(user);
        when(event.getGuild()).thenReturn(guild);
        when(user.getId()).thenReturn("123");
        when(guild.getId()).thenReturn("456");
        when(event.deferEdit()).thenReturn(deferEdit);

        // Act
        handler.onButtonInteraction(event);

        // Assert
        verify(event).deferEdit();
    }

    @Test
    void onButtonInteraction_wrongUser_sendsError() {
        // Arrange
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);
        User user = mock(User.class);

        when(event.getComponentId()).thenReturn("char-create:123:submit");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("999"); // Different user
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        // Act
        handler.onButtonInteraction(event);

        // Assert
        verify(event).reply(contains("not for you"));
        verify(replyAction).setEphemeral(true);
    }
}
