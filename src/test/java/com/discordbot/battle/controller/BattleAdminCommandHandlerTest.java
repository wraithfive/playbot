package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattlePermissionService;
import com.discordbot.battle.service.BattleService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BattleAdminCommandHandler
 * Phase 11: Security & Permissions
 */
class BattleAdminCommandHandlerTest {

    private BattleProperties battleProperties;
    private BattleService battleService;
    private BattlePermissionService permissionService;
    private BattleAdminCommandHandler handler;
    private SlashCommandInteractionEvent event;
    private Guild guild;
    private User user;
    private ReplyCallbackAction replyAction;

    @BeforeEach
    void setUp() {
        battleProperties = mock(BattleProperties.class);
        battleService = mock(BattleService.class);
        permissionService = mock(BattlePermissionService.class);
        handler = new BattleAdminCommandHandler(battleProperties, battleService, permissionService);

        event = mock(SlashCommandInteractionEvent.class);
        guild = mock(Guild.class);
        user = mock(User.class);
        replyAction = mock(ReplyCallbackAction.class);

        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("admin123");
        when(guild.getId()).thenReturn("guild123");
        when(guild.getName()).thenReturn("Test Guild");
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    }

    @Test
    void testCanHandleWhenDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);

        assertFalse(handler.canHandle("battle-cancel"));
        assertFalse(handler.canHandle("battle-config-reload"));
    }

    @Test
    void testCanHandleBattleCancel() {
        when(battleProperties.isEnabled()).thenReturn(true);

        assertTrue(handler.canHandle("battle-cancel"));
    }

    @Test
    void testCanHandleBattleConfigReload() {
        when(battleProperties.isEnabled()).thenReturn(true);

        assertTrue(handler.canHandle("battle-config-reload"));
    }

    @Test
    void testCanHandleOtherCommands() {
        when(battleProperties.isEnabled()).thenReturn(true);

        assertFalse(handler.canHandle("duel"));
        assertFalse(handler.canHandle("battle-help"));
    }

    @Test
    void testBattleCancelWithoutGuild() {
        when(event.getGuild()).thenReturn(null);
        when(event.getName()).thenReturn("battle-cancel");

        handler.handle(event);

        verify(event).reply("❌ This command can only be used in a server.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void testBattleCancelWithoutPermission() {
        when(event.getName()).thenReturn("battle-cancel");
        when(permissionService.hasAdminPermission(guild, "admin123")).thenReturn(false);
        when(permissionService.getPermissionDeniedMessage()).thenReturn("❌ No permission");

        handler.handle(event);

        verify(event).reply("❌ No permission");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void testBattleCancelWithoutBattleId() {
        when(event.getName()).thenReturn("battle-cancel");
        when(permissionService.hasAdminPermission(guild, "admin123")).thenReturn(true);
        when(event.getOption("battle_id")).thenReturn(null);

        handler.handle(event);

        verify(event).reply("❌ You must provide a battle ID.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void testBattleCancelSuccess() {
        when(event.getName()).thenReturn("battle-cancel");
        when(permissionService.hasAdminPermission(guild, "admin123")).thenReturn(true);

        OptionMapping battleIdOption = mock(OptionMapping.class);
        when(battleIdOption.getAsString()).thenReturn("battle123");
        when(event.getOption("battle_id")).thenReturn(battleIdOption);

        ActiveBattle battle = new ActiveBattle("battle123", "guild123", "challenger123", "opponent123");
        when(battleService.adminCancelBattle("battle123", "admin123")).thenReturn(battle);

        ReplyCallbackAction embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        verify(battleService).adminCancelBattle("battle123", "admin123");

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertTrue(embed.getTitle().contains("Cancelled"), "Embed title should indicate cancellation");
        verify(embedReplyAction).setEphemeral(false); // Public reply so participants can see
    }

    @Test
    void testBattleCancelBattleNotFound() {
        when(event.getName()).thenReturn("battle-cancel");
        when(permissionService.hasAdminPermission(guild, "admin123")).thenReturn(true);

        OptionMapping battleIdOption = mock(OptionMapping.class);
        when(battleIdOption.getAsString()).thenReturn("invalid");
        when(event.getOption("battle_id")).thenReturn(battleIdOption);

        when(battleService.adminCancelBattle("invalid", "admin123"))
            .thenThrow(new IllegalArgumentException("Battle not found: invalid"));

        handler.handle(event);

        verify(event).reply("❌ Battle not found: invalid");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void testConfigReloadWithoutPermission() {
        when(event.getName()).thenReturn("battle-config-reload");
        when(permissionService.hasAdminPermission(guild, "admin123")).thenReturn(false);
        when(permissionService.getPermissionDeniedMessage()).thenReturn("❌ No permission");

        handler.handle(event);

        verify(event).reply("❌ No permission");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void testConfigReloadSuccess() {
        when(event.getName()).thenReturn("battle-config-reload");
        when(permissionService.hasAdminPermission(guild, "admin123")).thenReturn(true);
        when(battleProperties.isEnabled()).thenReturn(true);
        when(battleProperties.isDebug()).thenReturn(false);

        // Mock character config
        BattleProperties.CharacterConfig characterConfig = mock(BattleProperties.CharacterConfig.class);
        BattleProperties.CharacterConfig.PointBuyConfig pointBuy = mock(BattleProperties.CharacterConfig.PointBuyConfig.class);
        when(battleProperties.getCharacter()).thenReturn(characterConfig);
        when(characterConfig.getPointBuy()).thenReturn(pointBuy);
        when(pointBuy.getTotalPoints()).thenReturn(27);
        when(pointBuy.getMinScore()).thenReturn(8);
        when(pointBuy.getMaxScore()).thenReturn(15);

        // Mock combat config
        BattleProperties.CombatConfig combatConfig = mock(BattleProperties.CombatConfig.class);
        BattleProperties.CombatConfig.CritConfig critConfig = mock(BattleProperties.CombatConfig.CritConfig.class);
        when(battleProperties.getCombat()).thenReturn(combatConfig);
        when(combatConfig.getCrit()).thenReturn(critConfig);
        when(critConfig.getThreshold()).thenReturn(20);
        when(critConfig.getMultiplier()).thenReturn(2.0);
        when(combatConfig.getDefendAcBonus()).thenReturn(2);
        when(combatConfig.getCooldownSeconds()).thenReturn(60);

        // Mock challenge config
        BattleProperties.ChallengeConfig challengeConfig = mock(BattleProperties.ChallengeConfig.class);
        when(battleProperties.getChallenge()).thenReturn(challengeConfig);
        when(challengeConfig.getExpireSeconds()).thenReturn(300);

        ReplyCallbackAction embedReplyAction = mock(ReplyCallbackAction.class);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(embedReplyAction);
        when(embedReplyAction.setEphemeral(anyBoolean())).thenReturn(embedReplyAction);

        handler.handle(event);

        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertTrue(embed.getTitle().contains("Configuration"), "Embed title should mention configuration");
        assertTrue(embed.getDescription().contains("restart"), "Description should mention restart requirement");
        verify(embedReplyAction).setEphemeral(true); // Private reply
    }
}
