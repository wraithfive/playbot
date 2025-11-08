package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.CharacterValidationService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TDD tests for /create-character command handler.
 */
class CreateCharacterCommandHandlerTest {

    private CreateCharacterCommandHandler handler;
    private CharacterValidationService validationService;
    private PlayerCharacterRepository repository;
    private BattleProperties battleProperties;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        battleProperties = new BattleProperties();
        battleProperties.setEnabled(true);
        
        validationService = new CharacterValidationService(battleProperties);
        repository = mock(PlayerCharacterRepository.class);
        handler = new CreateCharacterCommandHandler(battleProperties, validationService, repository);
        
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
        
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
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
    void handle_validCharacter_repliesWithSuccessEmbed() {
        // Mock command options for valid character (27 points: 15+14+13+12+10+8)
        mockOption(event, "class", "Warrior");
        mockOption(event, "race", "Human");
        mockOption(event, "strength", 15);
        mockOption(event, "dexterity", 14);
        mockOption(event, "constitution", 13);
        mockOption(event, "intelligence", 12);
        mockOption(event, "wisdom", 10);
        mockOption(event, "charisma", 8);

        // Mock repository.save() to return a saved character
        PlayerCharacter saved = new PlayerCharacter("123456789", "987654321",
            "Warrior", "Human", 15, 14, 13, 12, 10, 8);
        when(repository.save(any())).thenReturn(saved);

        handler.handle(event);

        // Verify ephemeral embed reply
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());
        verify(replyAction).setEphemeral(true);

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed.getTitle());
        assertTrue(embed.getTitle().contains("Character Created"));
        
        // Verify character details in embed
        String description = embed.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Warrior"));
        assertTrue(description.contains("Human"));
        
        // Verify ability scores in fields
        assertNotNull(embed.getFields());
        assertTrue(embed.getFields().size() > 0);
    }

    @Test
    void handle_invalidPointBuy_repliesWithErrorMessage() {
        // Mock invalid character (over budget: all 15s = 54 points)
        mockOption(event, "class", "Rogue");
        mockOption(event, "race", "Elf");
        mockOption(event, "strength", 15);
        mockOption(event, "dexterity", 15);
        mockOption(event, "constitution", 15);
        mockOption(event, "intelligence", 15);
        mockOption(event, "wisdom", 15);
        mockOption(event, "charisma", 15);

        handler.handle(event);

        // Verify error message
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        verify(replyAction).setEphemeral(true);

        String errorMessage = messageCaptor.getValue();
        assertTrue(errorMessage.contains("Invalid"));
        assertTrue(errorMessage.contains("27"));
    }

    @Test
    void handle_invalidClass_repliesWithErrorMessage() {
        mockOption(event, "class", "Bard");
        mockOption(event, "race", "Human");
        mockOption(event, "strength", 15);
        mockOption(event, "dexterity", 14);
        mockOption(event, "constitution", 13);
        mockOption(event, "intelligence", 12);
        mockOption(event, "wisdom", 10);
        mockOption(event, "charisma", 8);

        handler.handle(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        verify(replyAction).setEphemeral(true);

        String errorMessage = messageCaptor.getValue();
        assertTrue(errorMessage.contains("Invalid"));
        assertTrue(errorMessage.contains("class") || errorMessage.contains("race"));
    }

    @Test
    void handle_scoreOutOfRange_repliesWithErrorMessage() {
        mockOption(event, "class", "Mage");
        mockOption(event, "race", "Dwarf");
        mockOption(event, "strength", 7); // Below minimum of 8
        mockOption(event, "dexterity", 14);
        mockOption(event, "constitution", 13);
        mockOption(event, "intelligence", 15);
        mockOption(event, "wisdom", 12);
        mockOption(event, "charisma", 10);

        handler.handle(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        verify(replyAction).setEphemeral(true);

        String errorMessage = messageCaptor.getValue();
        assertTrue(errorMessage.contains("Invalid"));
        assertTrue(errorMessage.contains("8") && errorMessage.contains("15"));
    }

    @Test
    void handle_characterAlreadyExists_returnsError() {
        // Arrange
        PlayerCharacter existing = new PlayerCharacter("123456789", "987654321", 
            "Warrior", "Human", 15, 14, 13, 12, 10, 8);
        when(repository.findByUserIdAndGuildId("123456789", "987654321"))
            .thenReturn(Optional.of(existing));
        
        mockOption(event, "class", "Mage");
        mockOption(event, "race", "Elf");
        mockOption(event, "strength", 15);
        mockOption(event, "dexterity", 14);
        mockOption(event, "constitution", 13);
        mockOption(event, "intelligence", 12);
        mockOption(event, "wisdom", 10);
        mockOption(event, "charisma", 8);

        // Act
        handler.handle(event);

        // Assert
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        verify(replyAction).setEphemeral(true);
        verify(repository, never()).save(any());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("already have a character"));
    }

    @Test
    void handle_validCharacter_savesAndReturnsEmbed() {
        // Arrange
        mockOption(event, "class", "Warrior");
        mockOption(event, "race", "Human");
        mockOption(event, "strength", 15);
        mockOption(event, "dexterity", 14);
        mockOption(event, "constitution", 13);
        mockOption(event, "intelligence", 12);
        mockOption(event, "wisdom", 10);
        mockOption(event, "charisma", 8);

        PlayerCharacter saved = new PlayerCharacter("123456789", "987654321",
            "Warrior", "Human", 15, 14, 13, 12, 10, 8);
        when(repository.save(any())).thenReturn(saved);

        // Act
        handler.handle(event);

        // Assert
        verify(repository).save(any());
        verify(event).replyEmbeds(any(MessageEmbed.class));
        verify(replyAction).setEphemeral(true);
    }

    // Helper method to mock option values
    private void mockOption(SlashCommandInteractionEvent event, String name, String value) {
        OptionMapping option = mock(OptionMapping.class);
        when(option.getAsString()).thenReturn(value);
        when(event.getOption(name)).thenReturn(option);
    }

    private void mockOption(SlashCommandInteractionEvent event, String name, int value) {
        OptionMapping option = mock(OptionMapping.class);
        when(option.getAsInt()).thenReturn(value);
        when(event.getOption(name)).thenReturn(option);
    }
}
