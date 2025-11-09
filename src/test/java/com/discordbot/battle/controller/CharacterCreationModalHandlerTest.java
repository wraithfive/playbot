package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.CharacterValidationService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for CharacterCreationModalHandler.
 * Tests critical business logic including stats parsing, validation, error handling, and D&D modifier calculation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CharacterCreationModalHandlerTest {

    @Mock
    private BattleProperties battleProperties;
    
    @Mock
    private BattleProperties.CharacterConfig characterConfig;
    
    @Mock
    private BattleProperties.CharacterConfig.PointBuyConfig pointBuyConfig;
    
    @Mock
    private CharacterValidationService validationService;
    
    @Mock
    private PlayerCharacterRepository repository;
    
    @Mock
    private ModalInteractionEvent event;
    
    @Mock
    private User user;
    
    @Mock
    private Guild guild;
    
    @Mock
    private ModalMapping classMapping;
    
    @Mock
    private ModalMapping raceMapping;
    
    @Mock
    private ModalMapping statsMapping;
    
    @Mock
    private ReplyCallbackAction replyAction;

    private CharacterCreationModalHandler handler;

    @BeforeEach
    void setUp() {
        // Setup config mocks (lenient since not all tests use these)
        lenient().when(battleProperties.getCharacter()).thenReturn(characterConfig);
        lenient().when(characterConfig.getPointBuy()).thenReturn(pointBuyConfig);
        lenient().when(pointBuyConfig.getTotalPoints()).thenReturn(27);
        lenient().when(pointBuyConfig.getMinScore()).thenReturn(8);
        lenient().when(pointBuyConfig.getMaxScore()).thenReturn(15);

        handler = new CharacterCreationModalHandler(battleProperties, validationService, repository);

        // Setup common event mocks (lenient since not all tests use these)
        lenient().when(event.getUser()).thenReturn(user);
        lenient().when(event.getGuild()).thenReturn(guild);
        lenient().when(user.getId()).thenReturn("123456");
        lenient().when(guild.getId()).thenReturn("789012");
        
        // Setup reply mock chain (lenient since not all tests use these)
        lenient().when(event.reply(anyString())).thenReturn(replyAction);
        lenient().when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        lenient().when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    }

    @Test
    void onModalInteraction_wrongModalId_ignoresEvent() {
        when(event.getModalId()).thenReturn("some-other-modal");

        handler.onModalInteraction(event);

        verify(event, never()).reply(anyString());
    }

    @Test
    void onModalInteraction_wrongUserId_rejectsWithError() {
        when(event.getModalId()).thenReturn("char-create:999999");
        when(user.getId()).thenReturn("123456"); // Different from modal ID

        handler.onModalInteraction(event);

        verify(event).reply("❌ This character creation form is not for you!");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void onModalInteraction_correctUserId_processesRequest() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("15 14 13 12 10 8");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(true);
        PlayerCharacter mockChar = new PlayerCharacter("123456", "789012", "Warrior", "Human", 15, 14, 13, 12, 10, 8);
        when(repository.save(any())).thenReturn(mockChar);

        handler.onModalInteraction(event);

        verify(repository).save(any(PlayerCharacter.class));
    }

    @Test
    void handleCharacterCreation_characterAlreadyExists_rejectsWithError() {
        when(event.getModalId()).thenReturn("char-create:123456");
        PlayerCharacter existing = new PlayerCharacter("123456", "789012", "Warrior", "Human", 15, 14, 13, 12, 10, 8);
        when(repository.findByUserIdAndGuildId("123456", "789012"))
            .thenReturn(Optional.of(existing));

        handler.onModalInteraction(event);

        verify(event).reply("❌ You already have a character in this server!");
        verify(repository, never()).save(any());
    }

    @Test
    void parseStats_validInput_returnsArray() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("15 14 13 12 10 8");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(true);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        handler.onModalInteraction(event);

        ArgumentCaptor<PlayerCharacter> captor = ArgumentCaptor.forClass(PlayerCharacter.class);
        verify(repository).save(captor.capture());
        
        PlayerCharacter saved = captor.getValue();
        assertEquals(15, saved.getStrength());
        assertEquals(14, saved.getDexterity());
        assertEquals(13, saved.getConstitution());
        assertEquals(12, saved.getIntelligence());
        assertEquals(10, saved.getWisdom());
        assertEquals(8, saved.getCharisma());
    }

    @Test
    void parseStats_invalidFormat_tooFewNumbers_rejectsWithError() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("15 14 13"); // Only 3 stats
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());

        handler.onModalInteraction(event);

        verify(event).reply(contains("Invalid stat format"));
        verify(repository, never()).save(any());
    }

    @Test
    void parseStats_invalidFormat_tooManyNumbers_rejectsWithError() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("15 14 13 12 10 8 7"); // 7 stats
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());

        handler.onModalInteraction(event);

        verify(event).reply(contains("Invalid stat format"));
        verify(repository, never()).save(any());
    }

    @Test
    void parseStats_invalidFormat_nonNumeric_rejectsWithError() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("15 14 thirteen 12 10 8");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());

        handler.onModalInteraction(event);

        verify(event).reply(contains("Invalid stat format"));
        verify(repository, never()).save(any());
    }

    @Test
    void validation_invalidCharacter_showsSpecificErrors() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Bard"); // Invalid class
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("15 14 13 12 10 8");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(false);
        when(validationService.isValidClass("Bard")).thenReturn(false);
        when(validationService.isValidRace("Human")).thenReturn(true);
        when(validationService.calculatePointBuyTotal(any())).thenReturn(27);

        handler.onModalInteraction(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        
        String errorMessage = messageCaptor.getValue();
        assertTrue(errorMessage.contains("Invalid class"), "Error should mention invalid class");
        assertTrue(errorMessage.contains("Bard"), "Error should show the invalid value");
        assertFalse(errorMessage.contains("Invalid race"), "Error should not mention race if valid");
    }

    @Test
    void validation_invalidRace_showsSpecificError() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Orc"); // Invalid race
        when(statsMapping.getAsString()).thenReturn("15 14 13 12 10 8");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(false);
        when(validationService.isValidClass("Warrior")).thenReturn(true);
        when(validationService.isValidRace("Orc")).thenReturn(false);
        when(validationService.calculatePointBuyTotal(any())).thenReturn(27);

        handler.onModalInteraction(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        
        String errorMessage = messageCaptor.getValue();
        assertTrue(errorMessage.contains("Invalid race"), "Error should mention invalid race");
        assertTrue(errorMessage.contains("Orc"), "Error should show the invalid value");
    }

    @Test
    void validation_statOutOfRange_showsSpecificError() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("18 14 13 12 10 8"); // 18 is above max
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(false);
        when(validationService.isValidClass("Warrior")).thenReturn(true);
        when(validationService.isValidRace("Human")).thenReturn(true);
        when(validationService.calculatePointBuyTotal(any())).thenReturn(27);

        handler.onModalInteraction(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        
        String errorMessage = messageCaptor.getValue();
        assertTrue(errorMessage.contains("STR"), "Error should mention which stat is out of range");
        assertTrue(errorMessage.contains("18"), "Error should show the invalid value");
        assertTrue(errorMessage.contains("out of range"), "Error should indicate range violation");
    }

    @Test
    void validation_wrongPointBuyTotal_showsSpecificError() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("15 15 15 15 15 15");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(false);
        when(validationService.isValidClass("Warrior")).thenReturn(true);
        when(validationService.isValidRace("Human")).thenReturn(true);
        when(validationService.calculatePointBuyTotal(any())).thenReturn(54); // Way over budget

        handler.onModalInteraction(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        
        String errorMessage = messageCaptor.getValue();
        assertTrue(errorMessage.contains("Point-buy"), "Error should mention point-buy");
        assertTrue(errorMessage.contains("54"), "Error should show actual points used");
        assertTrue(errorMessage.contains("27"), "Error should show expected points");
    }

    @Test
    void successfulCreation_savesCharacterWithCorrectData() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Rogue");
        when(raceMapping.getAsString()).thenReturn("Elf");
        when(statsMapping.getAsString()).thenReturn("10 15 12 13 14 8");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(true);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        handler.onModalInteraction(event);

        ArgumentCaptor<PlayerCharacter> captor = ArgumentCaptor.forClass(PlayerCharacter.class);
        verify(repository).save(captor.capture());
        
        PlayerCharacter saved = captor.getValue();
        assertEquals("123456", saved.getUserId());
        assertEquals("789012", saved.getGuildId());
        assertEquals("Rogue", saved.getCharacterClass());
        assertEquals("Elf", saved.getRace());
        assertEquals(10, saved.getStrength());
        assertEquals(15, saved.getDexterity());
        assertEquals(12, saved.getConstitution());
        assertEquals(13, saved.getIntelligence());
        assertEquals(14, saved.getWisdom());
        assertEquals(8, saved.getCharisma());
    }

    @Test
    void successfulCreation_sendsSuccessEmbed() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Cleric");
        when(raceMapping.getAsString()).thenReturn("Dwarf");
        when(statsMapping.getAsString()).thenReturn("14 10 15 12 13 8");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(true);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        handler.onModalInteraction(event);

        verify(event).replyEmbeds(any(MessageEmbed.class));
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void raceCondition_duplicateSaveAttempt_handlesGracefully() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("15 14 13 12 10 8");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(true);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate key"));

        handler.onModalInteraction(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event).reply(messageCaptor.capture());
        
        String errorMessage = messageCaptor.getValue();
        assertTrue(errorMessage.contains("already have a character"), "Should inform about duplicate");
        assertTrue(errorMessage.contains("Another request was processed first"), "Should explain race condition");
    }

    @Test
    void unexpectedError_handlesGracefully() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("15 14 13 12 10 8");
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenThrow(new RuntimeException("Database error"));

        handler.onModalInteraction(event);

        verify(event).reply(contains("error occurred"));
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void statsWithExtraWhitespace_parsesCorrectly() {
        when(event.getModalId()).thenReturn("char-create:123456");
        when(event.getValue("class")).thenReturn(classMapping);
        when(event.getValue("race")).thenReturn(raceMapping);
        when(event.getValue("stats")).thenReturn(statsMapping);
        when(classMapping.getAsString()).thenReturn("Warrior");
        when(raceMapping.getAsString()).thenReturn("Human");
        when(statsMapping.getAsString()).thenReturn("  15   14  13   12 10    8  "); // Extra whitespace
        when(repository.findByUserIdAndGuildId(anyString(), anyString())).thenReturn(Optional.empty());
        when(validationService.isValid(any())).thenReturn(true);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        handler.onModalInteraction(event);

        ArgumentCaptor<PlayerCharacter> captor = ArgumentCaptor.forClass(PlayerCharacter.class);
        verify(repository).save(captor.capture());
        
        PlayerCharacter saved = captor.getValue();
        assertEquals(15, saved.getStrength());
        assertEquals(8, saved.getCharisma());
    }
}
