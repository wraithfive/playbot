package com.discordbot.battle.controller;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CharacterAutocompleteHandlerTest {

    private CharacterAutocompleteHandler handler;
    private CommandAutoCompleteInteractionEvent event;
    private AutoCompleteQuery focusedOption;
    private AutoCompleteCallbackAction callbackAction;

    @BeforeEach
    void setUp() {
        handler = new CharacterAutocompleteHandler();
        event = mock(CommandAutoCompleteInteractionEvent.class);
        focusedOption = mock(AutoCompleteQuery.class);
        callbackAction = mock(AutoCompleteCallbackAction.class);
        
        when(event.getFocusedOption()).thenReturn(focusedOption);
        when(event.replyChoices(anyList())).thenReturn(callbackAction);
    }

    @Test
    void handleCreateCharacterAutocomplete_classFocused_returnsValidClasses() {
        // Arrange
        when(focusedOption.getName()).thenReturn("class");

        // Act
        handler.handleCreateCharacterAutocomplete(event);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Command.Choice>> choicesCaptor = ArgumentCaptor.forClass(List.class);
        verify(event).replyChoices(choicesCaptor.capture());
        verify(callbackAction).queue();

        List<Command.Choice> choices = choicesCaptor.getValue();
        assertNotNull(choices);
        assertFalse(choices.isEmpty());
        
        // Verify choices are sorted and contain expected classes
        List<String> choiceNames = choices.stream()
            .map(Command.Choice::getName)
            .toList();
        assertTrue(choiceNames.contains("Cleric"));
        assertTrue(choiceNames.contains("Mage"));
        assertTrue(choiceNames.contains("Rogue"));
        assertTrue(choiceNames.contains("Warrior"));
        
        // Verify alphabetical order
        List<String> sortedNames = choiceNames.stream().sorted().toList();
        assertEquals(sortedNames, choiceNames);
    }

    @Test
    void handleCreateCharacterAutocomplete_raceFocused_returnsValidRaces() {
        // Arrange
        when(focusedOption.getName()).thenReturn("race");

        // Act
        handler.handleCreateCharacterAutocomplete(event);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Command.Choice>> choicesCaptor = ArgumentCaptor.forClass(List.class);
        verify(event).replyChoices(choicesCaptor.capture());
        verify(callbackAction).queue();

        List<Command.Choice> choices = choicesCaptor.getValue();
        assertNotNull(choices);
        assertFalse(choices.isEmpty());
        
        // Verify choices are sorted and contain expected races
        List<String> choiceNames = choices.stream()
            .map(Command.Choice::getName)
            .toList();
        assertTrue(choiceNames.contains("Dwarf"));
        assertTrue(choiceNames.contains("Elf"));
        assertTrue(choiceNames.contains("Halfling"));
        assertTrue(choiceNames.contains("Human"));
        
        // Verify alphabetical order
        List<String> sortedNames = choiceNames.stream().sorted().toList();
        assertEquals(sortedNames, choiceNames);
    }

    @Test
    void handleCreateCharacterAutocomplete_unknownOption_noReply() {
        // Arrange
        when(focusedOption.getName()).thenReturn("unknown");

        // Act
        handler.handleCreateCharacterAutocomplete(event);

        // Assert - should not reply for unknown options
        verify(event, never()).replyChoices(anyList());
    }

    @Test
    void handleCreateCharacterAutocomplete_choicesHaveMatchingValues() {
        // Arrange - test that choice name matches choice value
        when(focusedOption.getName()).thenReturn("class");

        // Act
        handler.handleCreateCharacterAutocomplete(event);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Command.Choice>> choicesCaptor = ArgumentCaptor.forClass(List.class);
        verify(event).replyChoices(choicesCaptor.capture());

        List<Command.Choice> choices = choicesCaptor.getValue();
        for (Command.Choice choice : choices) {
            // Name and value should be the same for character classes/races
            assertEquals(choice.getName(), choice.getAsString());
        }
    }
}
