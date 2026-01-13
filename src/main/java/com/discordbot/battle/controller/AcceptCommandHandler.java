package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattleService;
import com.discordbot.battle.util.BattleComponentIds;
import com.discordbot.battle.util.DiscordMentionUtils;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.Optional;

/**
 * Handles the /accept command which accepts a pending battle challenge.
 * This provides a command-based alternative to the Accept button.
 */
@Component
public class AcceptCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(AcceptCommandHandler.class);

    private final BattleProperties battleProperties;
    private final BattleService battleService;

    public AcceptCommandHandler(BattleProperties battleProperties, BattleService battleService) {
        this.battleProperties = battleProperties;
        this.battleService = battleService;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "accept".equals(commandName);
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

        // Find any pending battle where this user is the opponent
        Optional<ActiveBattle> pendingBattle = battleService.findPendingBattleForOpponent(userId);

        if (pendingBattle.isEmpty()) {
            event.reply("❌ You don't have any pending battle challenges.").setEphemeral(true).queue();
            return;
        }

        ActiveBattle battle = pendingBattle.get();

        // Verify the battle is in the same guild
        if (!guildId.equals(battle.getGuildId())) {
            event.reply("❌ The pending battle is in a different server.").setEphemeral(true).queue();
            return;
        }

        // Check if opponent has a character
        if (!battleService.hasCharacter(guildId, userId)) {
            event.reply("❌ You need to create a character first with /create-character before accepting.")
                .setEphemeral(true)
                .queue();
            return;
        }

        try {
            battleService.acceptChallenge(battle.getId(), userId);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(Color.GREEN);
            embed.setTitle("⚔️ Battle Started");
            embed.setDescription(String.format("%s vs %s",
                mention(battle.getChallengerId()),
                mention(battle.getOpponentId())));
            embed.addField("Challenger HP", String.valueOf(battle.getChallengerHp()), true);
            embed.addField("Opponent HP", String.valueOf(battle.getOpponentHp()), true);
            embed.addField("Status", battle.getStatus().name(), true);
            embed.addField("Turn", "It's " + mention(battle.getCurrentTurnUserId()) + "'s turn.", false);
            embed.setFooter("Battle ID: " + battle.getId());

            Button attack = Button.primary(componentId(battle.getId(), "attack"), "⚔️ Attack");
            Button defend = Button.secondary(componentId(battle.getId(), "defend"), "🛡️ Defend");
            Button forfeit = Button.danger(componentId(battle.getId(), "forfeit"), "🏳️ Forfeit");

            event.replyEmbeds(embed.build())
                .addComponents(ActionRow.of(attack, defend, forfeit))
                .queue(message -> {
                    // Store message/channel IDs for timeout notifications
                    String channelId = event.getChannel().getId();
                    String messageId = message.retrieveOriginal().complete().getId();
                    battleService.setBattleMessage(battle.getId(), channelId, messageId);
                });

            logger.info("Battle accepted via command: battleId={} user={}", battle.getId(), userId);
        } catch (IllegalStateException e) {
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Failed to accept battle", e);
            event.reply("❌ Failed to accept battle.").setEphemeral(true).queue();
        }
    }

    private String componentId(String battleId, String action) {
        return BattleComponentIds.componentId(battleId, action);
    }

    private String mention(String userId) {
        return DiscordMentionUtils.mention(userId);
    }
}
