package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.command.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.List;

/**
 * Handles the /leaderboard slash command to display rankings.
 * Phase 6: Progression & Leaderboards
 */
@Component
public class LeaderboardCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardCommandHandler.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 25;

    private final BattleProperties battleProperties;
    private final PlayerCharacterRepository characterRepository;

    public LeaderboardCommandHandler(BattleProperties battleProperties,
                                    PlayerCharacterRepository characterRepository) {
        this.battleProperties = battleProperties;
        this.characterRepository = characterRepository;
    }

    @Override
    public boolean canHandle(String commandName) {
        return battleProperties.isEnabled() && "leaderboard".equals(commandName);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        try {
            Guild guild = event.getGuild();
            if (guild == null) {
                event.reply("❌ This command must be used in a server (guild).").setEphemeral(true).queue();
                return;
            }

            // Get leaderboard type option (defaults to "elo")
            String type = event.getOption("type") != null
                ? event.getOption("type").getAsString()
                : "elo";

            // Get limit option (defaults to 10, max 25)
            int limit = event.getOption("limit") != null
                ? Math.min(event.getOption("limit").getAsInt(), MAX_LIMIT)
                : DEFAULT_LIMIT;

            String guildId = guild.getId();
            List<PlayerCharacter> topPlayers = getTopPlayers(guildId, type, limit);

            if (topPlayers.isEmpty()) {
                event.reply("📊 No characters found in this server yet. Create one with `/create-character`!")
                    .setEphemeral(true)
                    .queue();
                return;
            }

            EmbedBuilder embed = buildLeaderboardEmbed(type, topPlayers, guild);
            event.replyEmbeds(embed.build()).queue();

            logger.info("Leaderboard displayed: guild={} type={} limit={} results={}",
                guildId, type, limit, topPlayers.size());

        } catch (Exception e) {
            logger.error("Error displaying leaderboard", e);
            event.reply("❌ An error occurred while displaying the leaderboard.")
                .setEphemeral(true)
                .queue();
        }
    }

    /**
     * Get top players based on leaderboard type
     */
    private List<PlayerCharacter> getTopPlayers(String guildId, String type, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);

        return switch (type.toLowerCase()) {
            case "wins" -> characterRepository.findTopByWins(guildId, pageRequest);
            case "level" -> characterRepository.findTopByLevel(guildId, pageRequest);
            case "activity" -> characterRepository.findTopByActivity(guildId, pageRequest);
            default -> characterRepository.findTopByElo(guildId, pageRequest); // "elo" or default
        };
    }

    /**
     * Build leaderboard embed
     */
    private EmbedBuilder buildLeaderboardEmbed(String type, List<PlayerCharacter> topPlayers, Guild guild) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.GOLD);

        // Set title based on type
        String title = switch (type.toLowerCase()) {
            case "wins" -> "🏆 Top Warriors (by Wins)";
            case "level" -> "⭐ Top Levels";
            case "activity" -> "⚔️ Most Active Battlers";
            default -> "👑 ELO Leaderboard";
        };
        embed.setTitle(title);
        embed.setDescription("Server: " + guild.getName());

        // Build leaderboard entries
        StringBuilder leaderboard = new StringBuilder();
        for (int i = 0; i < topPlayers.size(); i++) {
            PlayerCharacter character = topPlayers.get(i);
            int rank = i + 1;

            // Medal emojis for top 3
            String medal = switch (rank) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "`" + rank + ".`";
            };

            // Get user mention (gracefully handle if user not found)
            String userMention = "<@" + character.getUserId() + ">";

            // Build stat line based on type
            String stats = switch (type.toLowerCase()) {
                case "wins" -> String.format("**%d** wins | %d-%d-%d | ELO: %d",
                    character.getWins(),
                    character.getWins(), character.getLosses(), character.getDraws(),
                    character.getElo());
                case "level" -> String.format("**Level %d** | %,d XP | ELO: %d",
                    character.getLevel(),
                    character.getXp(),
                    character.getElo());
                case "activity" -> {
                    int totalBattles = character.getWins() + character.getLosses() + character.getDraws();
                    yield String.format("**%d** battles | %d-%d-%d | ELO: %d",
                        totalBattles,
                        character.getWins(), character.getLosses(), character.getDraws(),
                        character.getElo());
                }
                default -> { // "elo"
                    double winRate = calculateWinRate(character);
                    yield String.format("**%d** ELO | %d-%d-%d | %.1f%% WR",
                        character.getElo(),
                        character.getWins(), character.getLosses(), character.getDraws(),
                        winRate);
                }
            };

            leaderboard.append(medal).append(" ")
                      .append(userMention).append(" • ")
                      .append(stats)
                      .append("\n");
        }

        embed.addField("Rankings", leaderboard.toString(), false);
        embed.setFooter("Use /leaderboard type:<elo|wins|level|activity> to change view");

        return embed;
    }

    /**
     * Calculate win rate percentage
     */
    private double calculateWinRate(PlayerCharacter character) {
        int totalBattles = character.getWins() + character.getLosses() + character.getDraws();
        if (totalBattles == 0) {
            return 0.0;
        }
        return (character.getWins() * 100.0) / totalBattles;
    }
}
