package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattlePermissionService;
import com.discordbot.battle.service.BattleService;
import com.discordbot.battle.util.DiscordMentionUtils;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;

/**
 * Admin-only command handler for battle system administration.
 * Phase 11: Security & Permissions
 *
 * Handles:
 * - /battle-cancel: Cancel an active or pending battle
 * - /battle-config-reload: View current configuration (note: changes require restart)
 */
@Component
public class BattleAdminCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(BattleAdminCommandHandler.class);

    private final BattleProperties battleProperties;
    private final BattleService battleService;
    private final BattlePermissionService permissionService;

    public BattleAdminCommandHandler(BattleProperties battleProperties,
                                     BattleService battleService,
                                     BattlePermissionService permissionService) {
        this.battleProperties = battleProperties;
        this.battleService = battleService;
        this.permissionService = permissionService;
    }

    @Override
    public boolean canHandle(String commandName) {
        if (!battleProperties.isEnabled()) {
            return false;
        }
        return "battle-cancel".equals(commandName) || "battle-config-reload".equals(commandName);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        // Guild context required
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        // Permission check
        if (!permissionService.hasAdminPermission(event.getGuild(), event.getUser().getId())) {
            event.reply(permissionService.getPermissionDeniedMessage()).setEphemeral(true).queue();
            return;
        }

        String commandName = event.getName();
        if ("battle-cancel".equals(commandName)) {
            handleBattleCancel(event);
        } else if ("battle-config-reload".equals(commandName)) {
            handleConfigReload(event);
        }
    }

    /**
     * Handle /battle-cancel command.
     * Cancels an active or pending battle by ID.
     */
    private void handleBattleCancel(SlashCommandInteractionEvent event) {
        String battleId = event.getOption("battle_id") != null
            ? event.getOption("battle_id").getAsString()
            : null;

        if (battleId == null || battleId.trim().isEmpty()) {
            event.reply("❌ You must provide a battle ID.").setEphemeral(true).queue();
            return;
        }

        try {
            ActiveBattle battle = battleService.adminCancelBattle(battleId.trim(), event.getUser().getId());

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("⚠️ Battle Cancelled by Administrator");
            embed.setColor(Color.ORANGE);
            embed.setDescription("Battle has been administratively cancelled.");
            embed.addField("Battle ID", battle.getId(), true);
            embed.addField("Guild", event.getGuild().getName(), true);
            embed.addField("Status", "Aborted", true);
            embed.addField("Participants",
                DiscordMentionUtils.mention(battle.getChallengerId()) + " vs " +
                DiscordMentionUtils.mention(battle.getOpponentId()),
                false);
            embed.addField("Cancelled By", DiscordMentionUtils.mention(event.getUser()), true);
            embed.setFooter("No XP or ELO awarded for cancelled battles");

            event.replyEmbeds(embed.build()).setEphemeral(false).queue(); // Public so participants can see

            logger.info("Admin cancelled battle: battleId={} admin={} guild={}",
                battleId, event.getUser().getId(), event.getGuild().getId());

        } catch (IllegalArgumentException e) {
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        } catch (IllegalStateException e) {
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Failed to cancel battle: battleId={}", battleId, e);
            event.reply("❌ Failed to cancel battle. Check logs for details.").setEphemeral(true).queue();
        }
    }

    /**
     * Handle /battle-config-reload command.
     * Shows current configuration status.
     * Note: Configuration changes require bot restart as BattleProperties uses @ConfigurationProperties.
     */
    private void handleConfigReload(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚙️ Battle System Configuration");
        embed.setColor(Color.CYAN);
        embed.setDescription(
            "Current battle system configuration. " +
            "**Note:** Configuration changes in `application.properties` require a bot restart to take effect."
        );

        // System status
        embed.addField("System Status",
            "Enabled: " + (battleProperties.isEnabled() ? "✅ Yes" : "❌ No") + "\n" +
            "Debug: " + (battleProperties.isDebug() ? "✅ Yes" : "❌ No"),
            false);

        // Character config
        if (battleProperties.getCharacter() != null && battleProperties.getCharacter().getPointBuy() != null) {
            var pointBuy = battleProperties.getCharacter().getPointBuy();
            embed.addField("Character Creation",
                "Point-Buy Total: " + pointBuy.getTotalPoints() + "\n" +
                "Min Score: " + pointBuy.getMinScore() + "\n" +
                "Max Score: " + pointBuy.getMaxScore(),
                true);
        }

        // Combat config
        if (battleProperties.getCombat() != null) {
            var combat = battleProperties.getCombat();
            embed.addField("Combat Settings",
                "Crit Threshold: " + combat.getCrit().getThreshold() + "\n" +
                "Crit Multiplier: " + combat.getCrit().getMultiplier() + "x\n" +
                "Defend AC Bonus: +" + combat.getDefendAcBonus() + "\n" +
                "Cooldown: " + combat.getCooldownSeconds() + "s",
                true);
        }

        // Challenge config
        if (battleProperties.getChallenge() != null) {
            var challenge = battleProperties.getChallenge();
            embed.addField("Challenge Settings",
                "Expire Seconds: " + challenge.getExpireSeconds(),
                true);
        }

        embed.setFooter("To apply changes, modify application.properties and restart the bot");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();

        logger.info("Admin viewed config: admin={} guild={}",
            event.getUser().getId(), event.getGuild().getId());
    }
}
