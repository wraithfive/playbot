package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattleService;
import com.discordbot.battle.util.CharacterCreationUIBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles Accept / Decline / Attack button interactions for battles.
 */
@Component
public class BattleInteractionHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(BattleInteractionHandler.class);

    private final BattleService battleService;
    private final BattleProperties battleProperties;
    private final CharacterCreationUIBuilder uiBuilder;

    public BattleInteractionHandler(BattleService battleService, 
                                   BattleProperties battleProperties,
                                   CharacterCreationUIBuilder uiBuilder) {
        this.battleService = battleService;
        this.battleProperties = battleProperties;
        this.uiBuilder = uiBuilder;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!battleProperties.isEnabled()) return; // Feature flag
        String id = event.getComponentId();
        if (!com.discordbot.battle.util.BattleComponentIds.isBattleComponent(id)) return; // Not a battle component

        String[] parts = id.split(":");
        if (parts.length != 3) {
            event.reply("❌ Invalid battle component.").setEphemeral(true).queue();
            return;
        }
        String battleId = parts[1];
        String action = parts[2];
        User clicker = event.getUser();

        ActiveBattle battle;
        try {
            battle = battleService.getBattleOrThrow(battleId);
        } catch (Exception e) {
            event.reply("❌ Battle not found or expired.").setEphemeral(true).queue();
            return;
        }

        // Authorization: only participants may interact
        boolean isParticipant = clicker.getId().equals(battle.getChallengerId()) || clicker.getId().equals(battle.getOpponentId());
        if (!isParticipant) {
            event.reply("❌ You're not part of this battle.").setEphemeral(true).queue();
            return;
        }

        try {
            switch (action) {
                case "accept" -> handleAccept(event, battle, clicker);
                case "decline" -> handleDecline(event, battle, clicker);
                case "attack" -> handleAttack(event, battle, clicker);
                case "createchar" -> handleCreateCharacter(event, battle, clicker);
                default -> event.reply("❌ Unknown action.").setEphemeral(true).queue();
            }
        } catch (IllegalStateException ise) {
            event.reply("❌ " + ise.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Error handling battle interaction", e);
            event.reply("❌ Action failed.").setEphemeral(true).queue();
        }
    }

    private void handleCreateCharacter(ButtonInteractionEvent event, ActiveBattle battle, User user) {
        // Only the challenged opponent should use this shortcut
        if (!user.getId().equals(battle.getOpponentId())) {
            event.reply("❌ Only the challenged opponent can create a character from this prompt.").setEphemeral(true).queue();
            return;
        }
        // If they already have a character, nudge them to accept instead
        if (battleService.hasCharacter(event.getGuild().getId(), user.getId())) {
            event.reply("✅ You already have a character. You can accept the challenge now.").setEphemeral(true).queue();
            return;
        }

        // Use the shared UI builder to create the character creation interface
        String userId = user.getId();
        var message = uiBuilder.buildCharacterCreationMessage(userId, false);
        event.reply(message)
            .setEphemeral(true)
            .queue();
    }

    private void handleAccept(ButtonInteractionEvent event, ActiveBattle battle, User user) {
        if (!battle.isPending()) {
            event.reply("❌ Battle already started.").setEphemeral(true).queue();
            return;
        }
        if (!user.getId().equals(battle.getOpponentId())) {
            event.reply("❌ Only the challenged opponent can accept.").setEphemeral(true).queue();
            return;
        }
        // If opponent doesn't have a character yet, guide them to create one.
        if (!battleService.hasCharacter(battle.getGuildId(), user.getId())) {
            event.reply("❌ You need to create a character first with /create-character before accepting.")
                .setEphemeral(true)
                .queue();
            return;
        }

        battleService.acceptChallenge(battle.getId(), user.getId());

        EmbedBuilder embed = buildBattleEmbed(battle);
        embed.setColor(Color.GREEN);
        embed.setTitle("⚔️ Battle Started");
        embed.addField("Turn", "It's " + mention(battle.getCurrentTurnUserId()) + "'s turn.", false);

        Button attack = Button.primary(componentId(battle.getId(), "attack"), "Attack");

        event.editMessageEmbeds(embed.build())
            .setComponents(ActionRow.of(attack))
            .queue();
    }

    private void handleDecline(ButtonInteractionEvent event, ActiveBattle battle, User user) {
        if (!battle.isPending()) {
            event.reply("❌ Cannot decline after battle starts.").setEphemeral(true).queue();
            return;
        }
        if (!user.getId().equals(battle.getOpponentId())) {
            event.reply("❌ Only the challenged opponent can decline.").setEphemeral(true).queue();
            return;
        }
        battleService.declineChallenge(battle.getId(), user.getId());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚔️ Duel Declined");
        embed.setColor(Color.RED);
        embed.setDescription(mention(battle.getOpponentId()) + " declined the challenge from " + mention(battle.getChallengerId()) + ".");
        embed.addField("Battle ID", battle.getId(), false);

        event.editMessageEmbeds(embed.build())
            .setComponents() // remove buttons
            .queue();
    }

    private void handleAttack(ButtonInteractionEvent event, ActiveBattle battle, User user) {
        if (!battle.isActive()) {
            event.reply("❌ Battle not active.").setEphemeral(true).queue();
            return;
        }
        if (!user.getId().equals(battle.getCurrentTurnUserId())) {
            event.reply("❌ Not your turn.").setEphemeral(true).queue();
            return;
        }

        var result = battleService.performAttack(battle.getId(), user.getId());

        EmbedBuilder embed = buildBattleEmbed(result.battle());
        embed.setTitle("⚔️ Battle");
        embed.setColor(Color.ORANGE);

        String outcome;
        if (!result.hit()) {
            outcome = mention(user.getId()) + " attacks and misses! (Roll " + result.rawRoll() + ")";
        } else if (result.crit()) {
            outcome = "💥 " + mention(user.getId()) + " CRITS for " + result.damage() + " damage! (Roll " + result.rawRoll() + ")";
        } else {
            outcome = mention(user.getId()) + " hits for " + result.damage() + " damage. (Roll " + result.rawRoll() + ")";
        }
        embed.addField("Action", outcome, false);

        if (result.winnerUserId() != null) {
            embed.setColor(Color.YELLOW);
            embed.addField("🏆 Winner", mention(result.winnerUserId()), false);
        } else {
            embed.addField("Turn", "Next: " + mention(result.battle().getCurrentTurnUserId()), false);
        }

        // Recent log entries
        List<String> recent = battle.getLogEntries().stream()
            .skip(Math.max(0, battle.getLogEntries().size() - 5))
            .map(ActiveBattle.BattleLogEntry::getMessage)
            .collect(Collectors.toList());
        if (!recent.isEmpty()) {
            embed.addField("Log (latest)", recent.stream().map(this::formatLogLine).collect(Collectors.joining("\n")), false);
        }

        if (result.winnerUserId() != null) {
            event.editMessageEmbeds(embed.build()).setComponents().queue();
        } else {
            Button attack = Button.primary(componentId(battle.getId(), "attack"), "Attack");
            event.editMessageEmbeds(embed.build()).setComponents(ActionRow.of(attack)).queue();
        }
    }

    private EmbedBuilder buildBattleEmbed(ActiveBattle battle) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setDescription(String.format("%s vs %s", mention(battle.getChallengerId()), mention(battle.getOpponentId())));
        embed.addField("Challenger HP", String.valueOf(battle.getChallengerHp()), true);
        embed.addField("Opponent HP", String.valueOf(battle.getOpponentHp()), true);
        embed.addField("Status", battle.getStatus().name(), true);
        embed.setFooter("Battle ID: " + battle.getId());
        return embed;
    }

    private String componentId(String battleId, String action) { return com.discordbot.battle.util.BattleComponentIds.componentId(battleId, action); }
    private String mention(String userId) { return com.discordbot.battle.util.DiscordMentionUtils.mention(userId); }

    // Accept only typical Discord snowflake lengths to avoid malformed replacements
    private static final Pattern USER_TOKEN = Pattern.compile("\\{USER:([0-9]{15,22})\\}");

    private String formatLogLine(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        Matcher m = USER_TOKEN.matcher(raw);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String id = m.group(1);
            // Replace with a mention; id is digits-only due to the pattern
            m.appendReplacement(sb, "<@" + id + ">");
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
