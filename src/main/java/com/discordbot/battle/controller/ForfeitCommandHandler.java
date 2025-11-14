package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattleService;
import com.discordbot.battle.util.DiscordMentionUtils;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.Optional;

/**
 * Handles the /forfeit command which forfeits an active battle.
 * This provides a command-based alternative to the Forfeit button.
 */
@Component
public class ForfeitCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(ForfeitCommandHandler.class);

    private final BattleProperties battleProperties;
    private final BattleService battleService;

    public ForfeitCommandHandler(BattleProperties battleProperties, BattleService battleService) {
        this.battleProperties = battleProperties;
        this.battleService = battleService;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "forfeit".equals(commandName);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        User user = event.getUser();
        String userId = user.getId();
        String guildId = event.getGuild().getId();

        // Find any active battle where this user is a participant
        Optional<ActiveBattle> activeBattle = battleService.findActiveBattleForUser(userId);

        if (activeBattle.isEmpty()) {
            event.reply("❌ You don't have any active battles.").setEphemeral(true).queue();
            return;
        }

        ActiveBattle battle = activeBattle.get();

        // Verify the battle is in the same guild
        if (!guildId.equals(battle.getGuildId())) {
            event.reply("❌ Your active battle is in a different server.").setEphemeral(true).queue();
            return;
        }

        try {
            battleService.forfeit(battle.getId(), userId);

            String winnerId = userId.equals(battle.getChallengerId())
                ? battle.getOpponentId()
                : battle.getChallengerId();

            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(Color.RED);
            embed.setTitle("🏳️ Battle Forfeited");
            embed.setDescription(String.format("%s has forfeited the battle!", mention(userId)));
            embed.addField("Winner", mention(winnerId), false);
            embed.addField("Battle ID", battle.getId(), false);
            embed.setFooter("Better luck next time!");

            event.replyEmbeds(embed.build()).queue();

            logger.info("Battle forfeited via command: battleId={} user={}", battle.getId(), userId);
        } catch (IllegalStateException e) {
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Failed to forfeit battle", e);
            event.reply("❌ Failed to forfeit battle.").setEphemeral(true).queue();
        }
    }

    private String mention(String userId) {
        return DiscordMentionUtils.mention(userId);
    }
}
