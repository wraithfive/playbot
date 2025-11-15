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

/**
 * Handles the /duel command which issues a battle challenge to another user.
 */
@Component
public class DuelCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(DuelCommandHandler.class);

    private final BattleProperties battleProperties;
    private final BattleService battleService;

    public DuelCommandHandler(BattleProperties battleProperties, BattleService battleService) {
        this.battleProperties = battleProperties;
        this.battleService = battleService;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "duel".equals(commandName);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        User challenger = event.getUser();
        User opponent = event.getOption("opponent") != null ? event.getOption("opponent").getAsUser() : null;
        if (opponent == null) {
            event.reply("❌ You must select an opponent.").setEphemeral(true).queue();
            return;
        }
        if (challenger.getId().equals(opponent.getId())) {
            event.reply("❌ You cannot duel yourself.").setEphemeral(true).queue();
            return;
        }
        if (opponent.isBot()) {
            event.reply("❌ You cannot challenge bots.").setEphemeral(true).queue();
            return;
        }

        try {
            // Check opponent character presence first for clearer validation flow.
            boolean opponentHasChar = battleService.hasCharacter(event.getGuild().getId(), opponent.getId());
            ActiveBattle battle = battleService.createChallenge(event.getGuild().getId(), challenger.getId(), opponent.getId());

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("⚔️ Duel Challenge");
            embed.setColor(Color.ORANGE);
            String baseDesc = String.format("%s has challenged %s to a duel!\n\nOpponent must click **Accept** or **Decline**.", mention(challenger), mention(opponent));
            if (!opponentHasChar) {
                baseDesc += "\n\nℹ️ " + mention(opponent) + " does not have a character yet. They must run /create-character before accepting.";
            }
            embed.setDescription(baseDesc);
            embed.addField("Status", "Pending acceptance", false);
            if (!opponentHasChar) {
                embed.addField("Opponent Character", "Not created", true);
            }
            embed.addField("Battle ID", battle.getId(), false);
            embed.setFooter("Use /battle-help for system info.");

            // Buttons: Accept / Decline (+ Create Character if opponent missing one)
            Button accept = Button.primary(componentId(battle.getId(), "accept"), "Accept");
            Button decline = Button.danger(componentId(battle.getId(), "decline"), "Decline");
            if (!opponentHasChar) {
                Button createChar = Button.secondary(componentId(battle.getId(), "createchar"), "Create Character");
                event.replyEmbeds(embed.build())
                    .addComponents(ActionRow.of(accept, decline, createChar))
                    .queue();
            } else {
                event.replyEmbeds(embed.build())
                    .addComponents(ActionRow.of(accept, decline))
                    .queue();
            }

            logger.info("Duel challenge issued battleId={} challenger={} opponent={}", battle.getId(), challenger.getId(), opponent.getId());
        } catch (IllegalStateException e) {
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Failed to create duel", e);
            event.reply("❌ Failed to create duel challenge.").setEphemeral(true).queue();
        }
    }

    private String componentId(String battleId, String action) {
        return BattleComponentIds.componentId(battleId, action);
    }

    private String mention(User user) { return DiscordMentionUtils.mention(user); }
}
