package com.discordbot.battle.controller;

import com.discordbot.battle.entity.CharacterConstants;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handles autocomplete interactions for battle system commands.
 * Provides autocomplete suggestions for character classes, races, and help topics.
 */
@Component
public class CharacterAutocompleteHandler {

    // Available help topics
    private static final List<String> HELP_TOPICS = List.of(
        "overview",
        "commands",
        "character",
        "combat",
        "abilities",
        "status",
        "progression"
    );

    private static final List<String> HELP_TOPIC_DESCRIPTIONS = List.of(
        "Overview - Quick start & feature summary",
        "Commands - All available slash commands",
        "Character - Stats, classes & character creation",
        "Combat - Battle mechanics & duels",
        "Abilities - Spells, skills & feats",
        "Status - Status effects explained",
        "Progression - XP, levels & leaderboards"
    );

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

    /**
     * Handles autocomplete for the battle-help command.
     * Provides descriptive choices for available help topics.
     *
     * @param event The autocomplete interaction event
     */
    public void handleBattleHelpAutocomplete(CommandAutoCompleteInteractionEvent event) {
        String focusedOption = event.getFocusedOption().getName();

        if ("topic".equals(focusedOption)) {
            List<Command.Choice> choices = List.of(
                new Command.Choice("Overview - Quick start & feature summary", "overview"),
                new Command.Choice("Commands - All available slash commands", "commands"),
                new Command.Choice("Character - Stats, classes & creation", "character"),
                new Command.Choice("Combat - Battle mechanics & duels", "combat"),
                new Command.Choice("Abilities - Spells, skills & feats", "abilities"),
                new Command.Choice("Status - Status effects explained", "status"),
                new Command.Choice("Progression - XP, levels & leaderboards", "progression")
            );
            event.replyChoices(choices).queue();
        }
    }
}
