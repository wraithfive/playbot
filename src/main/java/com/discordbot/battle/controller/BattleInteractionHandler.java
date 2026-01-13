package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.entity.Ability;
import com.discordbot.battle.entity.CharacterAbility;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.BattleService;
import com.discordbot.battle.util.CharacterCreationUIBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
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
 * Handles Accept / Decline / Attack / Defend / Forfeit button interactions for battles.
 */
@Component
public class BattleInteractionHandler extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(BattleInteractionHandler.class);

    private final BattleService battleService;
    private final BattleProperties battleProperties;
    private final CharacterCreationUIBuilder uiBuilder;
    private final com.discordbot.battle.service.StatusEffectService statusEffectService;
    private final PlayerCharacterRepository characterRepository;
    private final CharacterAbilityRepository characterAbilityRepository;

    public BattleInteractionHandler(BattleService battleService,
                                   BattleProperties battleProperties,
                                   CharacterCreationUIBuilder uiBuilder,
                                   com.discordbot.battle.service.StatusEffectService statusEffectService,
                                   PlayerCharacterRepository characterRepository,
                                   CharacterAbilityRepository characterAbilityRepository) {
        this.battleService = battleService;
        this.battleProperties = battleProperties;
        this.uiBuilder = uiBuilder;
        this.statusEffectService = statusEffectService;
        this.characterRepository = characterRepository;
        this.characterAbilityRepository = characterAbilityRepository;
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

        // Validate battleId format (UUID is 36 chars, but allow up to 50 for flexibility)
        String battleId = parts[1];
        if (battleId == null || battleId.length() > 50 || battleId.trim().isEmpty()) {
            logger.warn("Invalid battleId format in component interaction: length={}", battleId == null ? "null" : battleId.length());
            event.reply("❌ Invalid battle identifier.").setEphemeral(true).queue();
            return;
        }

        // Validate action is reasonable length
        String action = parts[2];
        if (action == null || action.length() > 20 || action.trim().isEmpty()) {
            logger.warn("Invalid action format in component interaction: length={}", action == null ? "null" : action.length());
            event.reply("❌ Invalid action type.").setEphemeral(true).queue();
            return;
        }

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
                case "defend" -> handleDefend(event, battle, clicker);
                case "ability" -> handleAbilityMenu(event, battle, clicker);
                case "forfeit" -> handleForfeit(event, battle, clicker);
                case "createchar" -> handleCreateCharacter(event, battle, clicker);
                default -> event.reply("❌ Unknown action.").setEphemeral(true).queue();
            }
        } catch (IllegalStateException ise) {
            // Battle state issues (e.g., not active, not your turn)
            event.reply("❌ " + ise.getMessage()).setEphemeral(true).queue();
        } catch (IllegalArgumentException iae) {
            // Invalid arguments (e.g., invalid ability, bad parameters)
            logger.warn("Invalid battle action: battleId={}, userId={}, action={}, error={}",
                       battleId, clicker.getId(), action, iae.getMessage());
            event.reply("❌ Invalid action: " + iae.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            // Unexpected errors
            logger.error("Unexpected error handling battle interaction: battleId={}, userId={}, action={}",
                        battleId, clicker.getId(), action, e);
            event.reply("❌ An unexpected error occurred. Please try again or contact support.").setEphemeral(true).queue();
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

        Button attack = Button.primary(componentId(battle.getId(), "attack"), "⚔️ Attack");
        Button defend = Button.secondary(componentId(battle.getId(), "defend"), "🛡️ Defend");
        Button ability = Button.success(componentId(battle.getId(), "ability"), "✨ Ability");
        Button forfeit = Button.danger(componentId(battle.getId(), "forfeit"), "🏳️ Forfeit");

        event.editMessageEmbeds(embed.build())
            .setComponents(ActionRow.of(attack, defend, ability, forfeit))
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

        // Show status effect messages if any (DoT, HoT, stun)
        if (result.statusEffectMessages() != null && !result.statusEffectMessages().isBlank()) {
            embed.addField("💫 Status Effects", result.statusEffectMessages().trim(), false);
        }

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
            // Award XP and ELO to both participants
            String winner = result.winnerUserId();
            String loser = winner.equals(battle.getChallengerId()) ? battle.getOpponentId() : battle.getChallengerId();
            battleService.awardProgressionRewards(winner, loser, battle.getGuildId(), false);

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
            Button attack = Button.primary(componentId(battle.getId(), "attack"), "⚔️ Attack");
            Button defend = Button.secondary(componentId(battle.getId(), "defend"), "🛡️ Defend");
            Button forfeit = Button.danger(componentId(battle.getId(), "forfeit"), "🏳️ Forfeit");
            event.editMessageEmbeds(embed.build()).setComponents(ActionRow.of(attack, defend, forfeit)).queue();
        }
    }

    private void handleDefend(ButtonInteractionEvent event, ActiveBattle battle, User user) {
        if (!battle.isActive()) {
            event.reply("❌ Battle not active.").setEphemeral(true).queue();
            return;
        }
        if (!user.getId().equals(battle.getCurrentTurnUserId())) {
            event.reply("❌ Not your turn.").setEphemeral(true).queue();
            return;
        }

        var result = battleService.performDefend(battle.getId(), user.getId());

        EmbedBuilder embed = buildBattleEmbed(result.battle());
        embed.setTitle("⚔️ Battle");
        embed.setColor(Color.BLUE);

        // Show status effect messages if any (DoT, HoT, stun)
        if (result.statusEffectMessages() != null && !result.statusEffectMessages().isBlank()) {
            embed.addField("💫 Status Effects", result.statusEffectMessages().trim(), false);
        }

        String outcome = "🛡️ " + mention(user.getId()) + " takes a defensive stance! (+" + result.tempAcBonus() + " AC next turn)";
        embed.addField("Action", outcome, false);

        if (result.winnerUserId() != null) {
            // Award XP and ELO to both participants
            String winner = result.winnerUserId();
            String loser = winner.equals(battle.getChallengerId()) ? battle.getOpponentId() : battle.getChallengerId();
            battleService.awardProgressionRewards(winner, loser, battle.getGuildId(), false);

            embed.setColor(Color.YELLOW);
            embed.addField("🏆 Winner", mention(result.winnerUserId()), false);
        } else {
            embed.addField("Turn", "Next: " + mention(result.battle().getCurrentTurnUserId()), false);
        }

        if (result.winnerUserId() != null) {
            event.editMessageEmbeds(embed.build()).setComponents().queue();
        } else {
            Button attack = Button.primary(componentId(battle.getId(), "attack"), "⚔️ Attack");
            Button defend = Button.secondary(componentId(battle.getId(), "defend"), "🛡️ Defend");
            Button ability = Button.success(componentId(battle.getId(), "ability"), "✨ Ability");
            Button forfeit = Button.danger(componentId(battle.getId(), "forfeit"), "🏳️ Forfeit");
            event.editMessageEmbeds(embed.build()).setComponents(ActionRow.of(attack, defend, ability, forfeit)).queue();
        }
    }

    private void handleForfeit(ButtonInteractionEvent event, ActiveBattle battle, User user) {
        if (!battle.isActive() && !battle.isPending()) {
            event.reply("❌ Battle not active or already ended.").setEphemeral(true).queue();
            return;
        }

        // Allow forfeit from either participant
        if (!user.getId().equals(battle.getChallengerId()) && !user.getId().equals(battle.getOpponentId())) {
            event.reply("❌ You're not part of this battle.").setEphemeral(true).queue();
            return;
        }

        battleService.forfeit(battle.getId(), user.getId());

        String winnerId = user.getId().equals(battle.getChallengerId())
            ? battle.getOpponentId()
            : battle.getChallengerId();

        // Award XP and ELO to both participants (forfeit counts as normal loss/win)
        battleService.awardProgressionRewards(winnerId, user.getId(), battle.getGuildId(), false);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.RED);
        embed.setTitle("🏳️ Battle Forfeited");
        embed.setDescription(String.format("%s has forfeited the battle!", mention(user.getId())));
        embed.addField("Winner", mention(winnerId), false);
        embed.addField("Battle ID", battle.getId(), false);
        embed.setFooter("Better luck next time!");

        event.editMessageEmbeds(embed.build()).setComponents().queue();
    }

    private void handleAbilityMenu(ButtonInteractionEvent event, ActiveBattle battle, User user) {
        if (!battle.isActive()) {
            event.reply("❌ Battle not active.").setEphemeral(true).queue();
            return;
        }
        if (!user.getId().equals(battle.getCurrentTurnUserId())) {
            event.reply("❌ Not your turn.").setEphemeral(true).queue();
            return;
        }

        // Get user's character and learned abilities
        PlayerCharacter character = characterRepository.findByUserIdAndGuildId(user.getId(), battle.getGuildId()).orElse(null);
        if (character == null) {
            event.reply("❌ Character not found.").setEphemeral(true).queue();
            return;
        }

        List<CharacterAbility> learned = characterAbilityRepository.findByCharacter(character);
        if (learned.isEmpty()) {
            event.reply("❌ You haven't learned any abilities yet. Use `/abilities` to learn some!").setEphemeral(true).queue();
            return;
        }

        // Build select menu with learned abilities (up to 25)
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("battle-ability:" + battle.getId() + ":" + user.getId())
            .setPlaceholder("Choose an ability to use");

        learned.stream()
            .limit(25) // Discord limit
            .forEach(ca -> {
                Ability ability = ca.getAbility();
                String label = ability.getName();
                String description = ability.getType();
                if (ability.getSpellSlotLevel() != null && ability.getSpellSlotLevel() > 0) {
                    description += " (Slot Lvl " + ability.getSpellSlotLevel() + ")";
                }
                if (ability.getCooldownSeconds() != null && ability.getCooldownSeconds() > 0) {
                    description += " (CD: " + ability.getCooldownSeconds() + "s)";
                }
                menuBuilder.addOption(label, String.valueOf(ability.getId()), description);
            });

        event.reply("✨ Choose an ability:")
            .addActionRow(menuBuilder.build())
            .setEphemeral(true)
            .queue();
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!battleProperties.isEnabled()) return;
        String id = event.getComponentId();
        if (!id.startsWith("battle-ability:")) return;

        String[] parts = id.split(":");
        if (parts.length != 3) {
            event.reply("❌ Invalid ability selection.").setEphemeral(true).queue();
            return;
        }

        String battleId = parts[1];
        String userId = parts[2];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("❌ This ability menu isn't for you.").setEphemeral(true).queue();
            return;
        }

        if (event.getValues().isEmpty()) {
            event.reply("❌ No ability selected.").setEphemeral(true).queue();
            return;
        }

        Long abilityId;
        try {
            abilityId = Long.parseLong(event.getValues().get(0));
        } catch (NumberFormatException e) {
            event.reply("❌ Invalid ability ID.").setEphemeral(true).queue();
            return;
        }

        // Get battle
        ActiveBattle battle;
        try {
            battle = battleService.getBattleOrThrow(battleId);
        } catch (Exception e) {
            event.reply("❌ Battle not found or expired.").setEphemeral(true).queue();
            return;
        }

        // Execute ability/spell
        handleAbilityExecution(event, battle, userId, abilityId);
    }

    private void handleAbilityExecution(StringSelectInteractionEvent event, ActiveBattle battle, String userId, Long abilityId) {
        try {
            var result = battleService.performSpell(battle.getId(), userId, abilityId);

            EmbedBuilder embed = buildBattleEmbed(result.battle());
            embed.setTitle("⚔️ Battle");
            embed.setColor(Color.MAGENTA);

            // Show status effect messages if any
            if (result.statusEffectMessages() != null && !result.statusEffectMessages().isBlank()) {
                embed.addField("💫 Status Effects", result.statusEffectMessages().trim(), false);
            }

            String outcome;
            if (!result.hit()) {
                outcome = "✨ " + mention(userId) + " casts " + result.abilityName() + " but misses! (Roll " + result.rawRoll() + ")";
            } else if (result.crit()) {
                outcome = "💥 " + mention(userId) + " casts " + result.abilityName() + " for a CRITICAL " + result.damage() + " damage! (Roll " + result.rawRoll() + ")";
            } else {
                outcome = "✨ " + mention(userId) + " casts " + result.abilityName() + " for " + result.damage() + " damage. (Roll " + result.rawRoll() + ")";
            }
            embed.addField("Action", outcome, false);

            if (result.winnerUserId() != null) {
                // Award XP and ELO
                String winner = result.winnerUserId();
                String loser = winner.equals(battle.getChallengerId()) ? battle.getOpponentId() : battle.getChallengerId();
                battleService.awardProgressionRewards(winner, loser, battle.getGuildId(), false);

                embed.setColor(Color.YELLOW);
                embed.addField("🏆 Winner", mention(result.winnerUserId()), false);
            } else {
                embed.addField("Turn", "Next: " + mention(result.battle().getCurrentTurnUserId()), false);
            }

            // Get the battle message to update
            if (battle.getChannelId() != null && battle.getMessageId() != null) {
                try {
                    var channel = event.getJDA().getTextChannelById(battle.getChannelId());
                    if (channel != null) {
                        if (result.winnerUserId() != null) {
                            channel.editMessageEmbedsById(battle.getMessageId(), embed.build()).setComponents().queue();
                        } else {
                            Button attack = Button.primary(componentId(battle.getId(), "attack"), "⚔️ Attack");
                            Button defend = Button.secondary(componentId(battle.getId(), "defend"), "🛡️ Defend");
                            Button ability = Button.success(componentId(battle.getId(), "ability"), "✨ Ability");
                            Button forfeit = Button.danger(componentId(battle.getId(), "forfeit"), "🏳️ Forfeit");
                            channel.editMessageEmbedsById(battle.getMessageId(), embed.build())
                                .setComponents(ActionRow.of(attack, defend, ability, forfeit))
                                .queue();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to update battle message after ability use", e);
                }
            }

            event.reply("✅ Ability used!").setEphemeral(true).queue();

        } catch (IllegalStateException | IllegalArgumentException e) {
            event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("Error executing ability in battle", e);
            event.reply("❌ Failed to execute ability. Please try again.").setEphemeral(true).queue();
        }
    }

    private EmbedBuilder buildBattleEmbed(ActiveBattle battle) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setDescription(String.format("%s vs %s", mention(battle.getChallengerId()), mention(battle.getOpponentId())));

        // Show challenger HP with active effects
        String challengerHpDisplay = String.valueOf(battle.getChallengerHp());
        String challengerEffects = statusEffectService.getEffectsDisplayString(battle.getId(), battle.getChallengerId());
        if (!challengerEffects.isEmpty()) {
            challengerHpDisplay += "\n" + challengerEffects;
        }
        embed.addField("Challenger HP", challengerHpDisplay, true);

        // Show opponent HP with active effects
        String opponentHpDisplay = String.valueOf(battle.getOpponentHp());
        String opponentEffects = statusEffectService.getEffectsDisplayString(battle.getId(), battle.getOpponentId());
        if (!opponentEffects.isEmpty()) {
            opponentHpDisplay += "\n" + opponentEffects;
        }
        embed.addField("Opponent HP", opponentHpDisplay, true);

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
