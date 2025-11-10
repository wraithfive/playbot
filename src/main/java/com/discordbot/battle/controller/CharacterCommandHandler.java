package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.util.CharacterDerivedStats;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Handles the /character slash command to view a character sheet.
 */
@Component
public class CharacterCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CharacterCommandHandler.class);

    private final BattleProperties battleProperties;
    private final PlayerCharacterRepository characterRepository;

    public CharacterCommandHandler(BattleProperties battleProperties,
                                   PlayerCharacterRepository characterRepository) {
        this.battleProperties = battleProperties;
        this.characterRepository = characterRepository;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "character".equals(commandName);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        try {
            var guild = event.getGuild();
            if (guild == null) {
                event.reply("❌ This command must be used in a server (guild).").setEphemeral(true).queue();
                return;
            }

            // Optional 'user' argument, defaults to the invoker
            String targetUserId = Optional.ofNullable(event.getOption("user"))
                .map(opt -> opt.getAsUser().getId())
                .orElse(event.getUser().getId());

            String guildId = guild.getId();

            Optional<PlayerCharacter> characterOpt = characterRepository.findByUserIdAndGuildId(targetUserId, guildId);
            if (characterOpt.isEmpty()) {
                if (targetUserId.equals(event.getUser().getId())) {
                    event.reply("❌ You don't have a character yet. Use /create-character to make one!")
                        .setEphemeral(true)
                        .queue();
                } else {
                    User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : null;
                    String name = targetUser != null ? targetUser.getName() : "that user";
                    event.reply("❌ " + name + " doesn't have a character in this server.")
                        .setEphemeral(true)
                        .queue();
                }
                return;
            }

            PlayerCharacter pc = characterOpt.get();

            // Build an embed with core stats and derived values
            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(Color.ORANGE);

            // Safely resolve display name (try Member first for server nickname, then fetch User from API)
            Member member = guild.getMemberById(pc.getUserId());
            String displayName;
            if (member != null) {
                displayName = member.getEffectiveName();
            } else {
                // User not in cache, fetch from Discord API
                try {
                    User user = event.getJDA().retrieveUserById(pc.getUserId()).complete();
                    displayName = user != null ? user.getName() : "Unknown User";
                } catch (Exception e) {
                    logger.warn("Failed to fetch user {} for character sheet: {}", pc.getUserId(), e.getMessage());
                    displayName = "Unknown User";
                }
            }
            embed.setTitle("🧙 Character Sheet — " + displayName);

            int str = pc.getStrength();
            int dex = pc.getDexterity();
            int con = pc.getConstitution();
            int intl = pc.getIntelligence();
            int wis = pc.getWisdom();
            int cha = pc.getCharisma();

            int strMod = CharacterDerivedStats.abilityMod(str);
            int dexMod = CharacterDerivedStats.abilityMod(dex);
            int conMod = CharacterDerivedStats.abilityMod(con);
            int intMod = CharacterDerivedStats.abilityMod(intl);
            int wisMod = CharacterDerivedStats.abilityMod(wis);
            int chaMod = CharacterDerivedStats.abilityMod(cha);

            int hp = CharacterDerivedStats.computeHp(pc, battleProperties);
            int ac = CharacterDerivedStats.computeAc(pc);

            embed.setDescription(
                "Class: **%s**\nRace: **%s**\nHP: **%d** | AC: **%d**".formatted(
                    pc.getCharacterClass(), pc.getRace(), hp, ac
                )
            );

            embed.addField("Strength", formatStat(str, strMod), true);
            embed.addField("Dexterity", formatStat(dex, dexMod), true);
            embed.addField("Constitution", formatStat(con, conMod), true);
            embed.addField("Intelligence", formatStat(intl, intMod), true);
            embed.addField("Wisdom", formatStat(wis, wisMod), true);
            embed.addField("Charisma", formatStat(cha, chaMod), true);

            embed.setFooter("Created " + pc.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } catch (Exception e) {
            logger.error("/character failed", e);
            event.reply("❌ Failed to show character.").setEphemeral(true).queue();
        }
    }

    private String formatStat(int score, int mod) {
        String sign = mod >= 0 ? "+" : "";
        return String.format("%d (%s%d)", score, sign, mod);
    }
}
