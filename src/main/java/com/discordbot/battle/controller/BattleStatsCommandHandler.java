package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.service.BattleMetricsService;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;

/**
 * Handles the /battle-stats slash command to display battle system metrics.
 * Phase 8: Monitoring & Logging
 */
@Component
public class BattleStatsCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(BattleStatsCommandHandler.class);

    private final BattleProperties battleProperties;
    private final BattleMetricsService metricsService;

    public BattleStatsCommandHandler(BattleProperties battleProperties,
                                    BattleMetricsService metricsService) {
        this.battleProperties = battleProperties;
        this.metricsService = metricsService;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "battle-stats".equals(commandName);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        try {
            Guild guild = event.getGuild();
            if (guild == null) {
                event.reply("❌ This command must be used in a server (guild).").setEphemeral(true).queue();
                return;
            }

            BattleMetricsService.BattleStats stats = metricsService.getStats();
            EmbedBuilder embed = buildStatsEmbed(stats, guild);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();

            logger.info("Battle stats displayed: guild={} user={}", guild.getId(), event.getUser().getId());

        } catch (Exception e) {
            logger.error("Error displaying battle stats", e);
            event.reply("❌ An error occurred while displaying battle stats.")
                .setEphemeral(true)
                .queue();
        }
    }

    /**
     * Build battle stats embed
     */
    private EmbedBuilder buildStatsEmbed(BattleMetricsService.BattleStats stats, Guild guild) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);
        embed.setTitle("⚔️ Battle System Statistics");
        embed.setDescription("Real-time metrics for the battle system");

        // Challenge stats
        embed.addField("🎯 Challenges",
            String.format("```" +
                "Created:  %,d\n" +
                "Accepted: %,d\n" +
                "Declined: %,d\n" +
                "Pending:  %d```",
                stats.challengesCreated(),
                stats.challengesAccepted(),
                stats.challengesDeclined(),
                stats.pendingChallenges()),
            true);

        // Battle completion stats
        long completedBattles = stats.battlesCompleted();
        long forfeitBattles = stats.battlesForfeit();
        long timeoutBattles = stats.battlesTimeout();
        long abortedBattles = stats.battlesAborted();
        long totalEnded = completedBattles + forfeitBattles + timeoutBattles + abortedBattles;

        embed.addField("🏁 Battle Outcomes",
            String.format("```" +
                "Completed: %,d\n" +
                "Forfeit:   %,d\n" +
                "Timeout:   %,d\n" +
                "Aborted:   %,d\n" +
                "Total:     %,d```",
                completedBattles,
                forfeitBattles,
                timeoutBattles,
                abortedBattles,
                totalEnded),
            true);

        // Current activity
        embed.addField("📊 Current Activity",
            String.format("```" +
                "Active Battles: %d\n" +
                "Pending:        %d```",
                stats.activeBattles(),
                stats.pendingChallenges()),
            false);

        // Combat action stats
        embed.addField("⚔️ Combat Actions",
            String.format("```" +
                "Attacks: %,d\n" +
                "Defends: %,d\n" +
                "Spells:  %,d\n" +
                "Crits:   %,d```",
                stats.attacksPerformed(),
                stats.defendsPerformed(),
                stats.spellsCast(),
                stats.criticalHits()),
            true);

        // Performance metrics
        embed.addField("⏱️ Performance",
            String.format("```" +
                "Avg Turn:   %.1f ms\n" +
                "Avg Battle: %.1f ms```",
                stats.avgTurnDurationMs(),
                stats.avgBattleDurationMs()),
            true);

        embed.setFooter("Metrics tracked since bot start • Phase 8: Monitoring");

        return embed;
    }
}
