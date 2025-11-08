package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BattleCommandHandler slash command interaction handling.
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
    }

    @Test
    void testBattleCommandIgnoredWhenDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);
        when(event.getName()).thenReturn("battle-help");

        handler.onSlashCommandInteraction(event);

        // Should not reply when disabled
        verify(event, never()).reply(anyString());
        verify(event, never()).replyEmbeds(any());
    }

    @Test
    void testNonBattleCommandIgnored() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("some-other-command");

        handler.onSlashCommandInteraction(event);

        // Should not reply to other commands
        verify(event, never()).reply(anyString());
        verify(event, never()).replyEmbeds(any());
    }

    @Test
    void testBattleHelpCommandSendsEmbed() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");
        
        // Mock the character config
        var character = mock(BattleProperties.CharacterConfig.class);
        var pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(battleProperties.getCharacter()).thenReturn(character);
        when(character.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);

        // Mock replyEmbeds
        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.onSlashCommandInteraction(event);

        // Capture the embed that was sent
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());
        verify(embedReplyAction).setEphemeral(true); // Help is ephemeral
        verify(embedReplyAction).queue();

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed, "Embed should not be null");
        assertEquals("⚔️ Battle System Guide", embed.getTitle(), 
            "Embed title should be Battle System Guide");
        assertEquals(Color.BLUE.getRGB(), embed.getColor().getRGB(), 
            "Embed color should be blue");
    }

    @Test
    void testBattleHelpIncludesCharacterStats() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");
        
        var character = mock(BattleProperties.CharacterConfig.class);
        var pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(battleProperties.getCharacter()).thenReturn(character);
        when(character.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        List<MessageEmbed.Field> fields = embed.getFields();
        
        assertTrue(fields.stream()
            .anyMatch(f -> f.getName().contains("Character Stats")),
            "Should have Character Stats field");
        
        assertTrue(fields.stream()
            .anyMatch(f -> f.getValue().contains("STR") && 
                          f.getValue().contains("DEX") && 
                          f.getValue().contains("CON")),
            "Character Stats should mention D&D 5e ability scores");
    }

    @Test
    void testBattleHelpIncludesPointBuyInfo() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");
        
        var character = mock(BattleProperties.CharacterConfig.class);
        var pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(battleProperties.getCharacter()).thenReturn(character);
        when(character.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(30); // Custom value
        when(pointBuy.getMinScore()).thenReturn(6);     // Custom value
        when(pointBuy.getMaxScore()).thenReturn(16);    // Custom value

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.onSlashCommandInteraction(event);

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
        
        var character = mock(BattleProperties.CharacterConfig.class);
        var pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(battleProperties.getCharacter()).thenReturn(character);
        when(character.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.onSlashCommandInteraction(event);

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
        
        var character = mock(BattleProperties.CharacterConfig.class);
        var pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(battleProperties.getCharacter()).thenReturn(character);
        when(character.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);

        var embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.onSlashCommandInteraction(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getName().contains("Combat Mechanics")),
            "Should have Combat Mechanics field");
        
        assertTrue(embed.getFields().stream()
            .anyMatch(f -> f.getValue().contains("d20") && 
                          f.getValue().contains("Critical")),
            "Should describe combat mechanics with d20 and crits");
    }

    @Test
    void testErrorHandlingReturnsEphemeralError() {
        when(battleProperties.isEnabled()).thenReturn(true);
        when(event.getName()).thenReturn("battle-help");
        
        // Cause an exception by returning null for character config
        when(battleProperties.getCharacter()).thenThrow(new RuntimeException("Test exception"));

        handler.onSlashCommandInteraction(event);

        verify(event).reply("❌ An error occurred while processing your command.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }
}
