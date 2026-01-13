package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BattleCommandHandler slash command interaction handling.
 * Tests the comprehensive topic-based help system (Phase 10).
 */
class BattleCommandHandlerTest {

    private BattleProperties battleProperties;
    private BattleCommandHandler handler;
    private SlashCommandInteractionEvent event;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        battleProperties = mock(BattleProperties.class);
        handler = new BattleCommandHandler(battleProperties);
        event = mock(SlashCommandInteractionEvent.class);
        replyAction = mock(ReplyCallbackAction.class);

        // Setup common mocks
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);

        // Mock character config by default
        var character = mock(BattleProperties.CharacterConfig.class);
        var pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(battleProperties.getCharacter()).thenReturn(character);
        when(character.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);
    }

    @Test
    void testBattleCommandIgnoredWhenDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);
        when(event.getName()).thenReturn("battle-help");

        // Handler should not be able to handle when disabled
        assertFalse(handler.canHandle("battle-help"),
            "Handler should not handle battle-help when disabled");
    }

    @Test
    void testNonBattleCommandIgnored() {
        when(battleProperties.isEnabled()).thenReturn(true);

        assertFalse(handler.canHandle("some-other-command"),
            "Handler should not handle non-battle commands");
    }

    @Test
    void testBattleHelpCommandSendsEmbed() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");
        when(event.getOption("topic")).thenReturn(null); // Defaults to overview

        // Mock replyEmbeds
        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        // Capture the embed that was sent
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());
        verify(embedReplyAction).setEphemeral(true); // Help is ephemeral
        verify(embedReplyAction).queue();

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed, "Embed should not be null");
        assertEquals("⚔️ Battle System - Overview", embed.getTitle(),
            "Embed title should be Battle System - Overview");
        assertEquals(Color.CYAN.getRGB(), embed.getColor().getRGB(),
            "Embed color should be cyan");
    }

    @Test
    void testBattleHelpOverviewIncludesKeyInfo() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");
        when(event.getOption("topic")).thenReturn(null); // Overview

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();

        // Overview should have help topics field
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Help Topics")),
            "Overview should have Help Topics field");

        // Should have quick start
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Quick Start")),
            "Overview should have Quick Start field");

        // Should have core features
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Core Features")),
            "Overview should have Core Features field");
    }

    @Test
    void testBattleHelpCharacterTopicIncludesStats() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");

        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("character");
        when(event.getOption("topic")).thenReturn(topicOption);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⚔️ Battle System - Characters", embed.getTitle());

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Ability Scores")),
            "Should have Ability Scores field");

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getValue().contains("STR") &&
                          f.getValue().contains("DEX") &&
                          f.getValue().contains("CON")),
            "Ability Scores should mention D&D 5e stats");
    }

    @Test
    void testBattleHelpIncludesPointBuyInfo() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");

        // Custom point buy values
        var character = mock(BattleProperties.CharacterConfig.class);
        var pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(battleProperties.getCharacter()).thenReturn(character);
        when(character.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(30); // Custom value
        when(pointBuy.getMinScore()).thenReturn(6);     // Custom value
        when(pointBuy.getMaxScore()).thenReturn(16);    // Custom value

        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("character");
        when(event.getOption("topic")).thenReturn(topicOption);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Point-Buy")),
            "Should have Point-Buy field");

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getValue().contains("30 points")),
            "Should show custom total points (30)");

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getValue().contains("6-16")),
            "Should show custom score range (6-16)");
    }

    @Test
    void testBattleHelpIncludesClassInfo() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");

        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("character");
        when(event.getOption("topic")).thenReturn(topicOption);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Classes")),
            "Should have Classes field");

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getValue().contains("Warrior") &&
                          f.getValue().contains("Rogue") &&
                          f.getValue().contains("Mage") &&
                          f.getValue().contains("Cleric")),
            "Should list all four character classes");
    }

    @Test
    void testBattleHelpIncludesCombatMechanics() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");

        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("combat");
        when(event.getOption("topic")).thenReturn(topicOption);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⚔️ Battle System - Combat", embed.getTitle());

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Combat Flow") || f.getName().contains("Attack Mechanics")),
            "Should have Combat mechanics field");

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getValue().contains("d20") &&
                          f.getValue().contains("Critical")),
            "Should describe combat mechanics with d20 and crits");
    }

    @Test
    void testBattleHelpCommandsTopic() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");

        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("commands");
        when(event.getOption("topic")).thenReturn(topicOption);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⚔️ Battle System - Commands", embed.getTitle());

        // Should list command categories
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Character Commands")),
            "Should have Character Commands section");

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Combat Commands")),
            "Should have Combat Commands section");
    }

    @Test
    void testBattleHelpStatusTopic() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");

        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("status");
        when(event.getOption("topic")).thenReturn(topicOption);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⚔️ Battle System - Status Effects", embed.getTitle());

        // Should have status effect categories
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Damage Over Time")),
            "Should have DoT section");

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getValue().contains("BURN") || f.getValue().contains("POISON")),
            "Should list status effects like BURN and POISON");
    }

    @Test
    void testBattleHelpProgressionTopic() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");

        OptionMapping topicOption = mock(OptionMapping.class);
        when(topicOption.getAsString()).thenReturn("progression");
        when(event.getOption("topic")).thenReturn(topicOption);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⚔️ Battle System - Progression", embed.getTitle());

        // Should have progression info
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Chat XP")),
            "Should have Chat XP section");

        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("ELO")),
            "Should have ELO ranking section");
    }

}
