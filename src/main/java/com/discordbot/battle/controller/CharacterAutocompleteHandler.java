package com.discordbot.battle.controller;

import com.discordbot.battle.entity.CharacterConstants;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handles autocomplete interactions for character-related commands.
 * Provides autocomplete suggestions for character classes and races.
 */
@Component
public class CharacterAutocompleteHandler {

    /**
     * Handles autocomplete for the create-character command.
     * 
     * @param event The autocomplete interaction event
     */
    public void handleCreateCharacterAutocomplete(CommandAutoCompleteInteractionEvent event) {
        String focusedOption = event.getFocusedOption().getName();
        
        if ("class".equals(focusedOption)) {
            List<Command.Choice> choices = CharacterConstants.VALID_CLASSES.stream()
                .sorted()
                .map(c -> new Command.Choice(c, c))
                .toList();
            event.replyChoices(choices).queue();
        } else if ("race".equals(focusedOption)) {
            List<Command.Choice> choices = CharacterConstants.VALID_RACES.stream()
                .sorted()
                .map(r -> new Command.Choice(r, r))
                .toList();
            event.replyChoices(choices).queue();
        }
    }
}
