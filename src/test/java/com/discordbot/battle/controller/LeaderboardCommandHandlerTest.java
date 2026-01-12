package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.entity.PlayerCharacterTestFactory;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeaderboardCommandHandler.
 * Tests leaderboard display for ELO, wins, level, and activity rankings.
 */
class LeaderboardCommandHandlerTest {

    @Mock
    private BattleProperties battleProperties;

    @Mock
    private PlayerCharacterRepository characterRepository;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private ReplyCallbackAction replyAction;

    private LeaderboardCommandHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(battleProperties.isEnabled()).thenReturn(true);
        handler = new LeaderboardCommandHandler(battleProperties, characterRepository);

        // Default mocks
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("222222222222222222");
        when(guild.getName()).thenReturn("Test Server");
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
    }

    @Test
    void canHandle_returnsTrueForLeaderboardWhenEnabled() {
        assertTrue(handler.canHandle("leaderboard"));
    }

    @Test
    void canHandle_returnsFalseWhenBattleSystemDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);
        assertFalse(handler.canHandle("leaderboard"));
    }

    @Test
    void canHandle_returnsFalseForOtherCommands() {
        assertFalse(handler.canHandle("duel"));
        assertFalse(handler.canHandle("character"));
        assertFalse(handler.canHandle("other"));
    }

    @Test
    void handle_rejectsDirectMessages() {
        // Given: No guild (DM)
        when(event.getGuild()).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Rejects with error
        verify(event).reply("❌ This command must be used in a server (guild).");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_displaysEloLeaderboard_byDefault() {
        // Given: Top ELO players
        List<PlayerCharacter> topPlayers = createMockPlayers(3);
        when(characterRepository.findTopByElo(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(topPlayers);
        when(event.getOption("type")).thenReturn(null); // Default to ELO
        when(event.getOption("limit")).thenReturn(null); // Default limit

        // When: Handle command
        handler.handle(event);

        // Then: Displays ELO leaderboard
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("👑 ELO Leaderboard", embed.getTitle());
        assertTrue(embed.getDescription().contains("Test Server"));
        verify(characterRepository).findTopByElo(eq("222222222222222222"), eq(PageRequest.of(0, 10)));
    }

    @Test
    void handle_displaysWinsLeaderboard() {
        // Given: Top players by wins
        List<PlayerCharacter> topPlayers = createMockPlayers(3);
        when(characterRepository.findTopByWins(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(topPlayers);

        OptionMapping typeOption = mock(OptionMapping.class);
        when(typeOption.getAsString()).thenReturn("wins");
        when(event.getOption("type")).thenReturn(typeOption);
        when(event.getOption("limit")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Displays wins leaderboard
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("🏆 Top Warriors (by Wins)", embed.getTitle());
        verify(characterRepository).findTopByWins(eq("222222222222222222"), eq(PageRequest.of(0, 10)));
    }

    @Test
    void handle_displaysLevelLeaderboard() {
        // Given: Top players by level
        List<PlayerCharacter> topPlayers = createMockPlayers(3);
        when(characterRepository.findTopByLevel(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(topPlayers);

        OptionMapping typeOption = mock(OptionMapping.class);
        when(typeOption.getAsString()).thenReturn("level");
        when(event.getOption("type")).thenReturn(typeOption);
        when(event.getOption("limit")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Displays level leaderboard
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⭐ Top Levels", embed.getTitle());
        verify(characterRepository).findTopByLevel(eq("222222222222222222"), eq(PageRequest.of(0, 10)));
    }

    @Test
    void handle_displaysActivityLeaderboard() {
        // Given: Most active players
        List<PlayerCharacter> topPlayers = createMockPlayers(3);
        when(characterRepository.findTopByActivity(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(topPlayers);

        OptionMapping typeOption = mock(OptionMapping.class);
        when(typeOption.getAsString()).thenReturn("activity");
        when(event.getOption("type")).thenReturn(typeOption);
        when(event.getOption("limit")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Displays activity leaderboard
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⚔️ Most Active Battlers", embed.getTitle());
        verify(characterRepository).findTopByActivity(eq("222222222222222222"), eq(PageRequest.of(0, 10)));
    }

    @Test
    void handle_respectsCustomLimit() {
        // Given: Custom limit of 5
        List<PlayerCharacter> topPlayers = createMockPlayers(5);
        when(characterRepository.findTopByElo(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(topPlayers);

        OptionMapping limitOption = mock(OptionMapping.class);
        when(limitOption.getAsInt()).thenReturn(5);
        when(event.getOption("limit")).thenReturn(limitOption);
        when(event.getOption("type")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Uses custom limit
        verify(characterRepository).findTopByElo(eq("222222222222222222"), eq(PageRequest.of(0, 5)));
    }

    @Test
    void handle_capsLimitAtMaximum() {
        // Given: Requested limit of 100 (exceeds max of 25)
        List<PlayerCharacter> topPlayers = createMockPlayers(25);
        when(characterRepository.findTopByElo(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(topPlayers);

        OptionMapping limitOption = mock(OptionMapping.class);
        when(limitOption.getAsInt()).thenReturn(100);
        when(event.getOption("limit")).thenReturn(limitOption);
        when(event.getOption("type")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Caps at 25
        verify(characterRepository).findTopByElo(eq("222222222222222222"), eq(PageRequest.of(0, 25)));
    }

    @Test
    void handle_handlesEmptyLeaderboard() {
        // Given: No characters in guild
        when(characterRepository.findTopByElo(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(List.of());
        when(event.getOption("type")).thenReturn(null);
        when(event.getOption("limit")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Displays helpful message
        verify(event).reply("📊 No characters found in this server yet. Create one with `/create-character`!");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_handlesExceptions() {
        // Given: Repository throws exception
        when(characterRepository.findTopByElo(any(), any()))
            .thenThrow(new RuntimeException("Database error"));
        when(event.getOption("type")).thenReturn(null);
        when(event.getOption("limit")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Returns error message
        verify(event).reply("❌ An error occurred while displaying the leaderboard.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_includesMedalEmojisForTopThree() {
        // Given: Top 5 players
        List<PlayerCharacter> topPlayers = createMockPlayers(5);
        when(characterRepository.findTopByElo(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(topPlayers);
        when(event.getOption("type")).thenReturn(null);
        when(event.getOption("limit")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Embed contains medal emojis
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field rankingsField = embed.getFields().get(0);
        String rankings = rankingsField.getValue();

        assertTrue(rankings.contains("🥇"), "Should contain gold medal for 1st place");
        assertTrue(rankings.contains("🥈"), "Should contain silver medal for 2nd place");
        assertTrue(rankings.contains("🥉"), "Should contain bronze medal for 3rd place");
        assertTrue(rankings.contains("`4.`"), "Should contain numeric rank for 4th place");
        assertTrue(rankings.contains("`5.`"), "Should contain numeric rank for 5th place");
    }

    @Test
    void handle_displaysCorrectStatsForEloLeaderboard() {
        // Given: Player with specific stats
        PlayerCharacter player = createPlayerWithStats("111111111111111111", 10, 5, 2, 1500);
        when(characterRepository.findTopByElo(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(List.of(player));
        when(event.getOption("type")).thenReturn(null);
        when(event.getOption("limit")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Displays ELO and win rate
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field rankingsField = embed.getFields().get(0);
        String rankings = rankingsField.getValue();

        assertTrue(rankings.contains("**1500** ELO"), "Should show ELO");
        assertTrue(rankings.contains("10-5-2"), "Should show W-L-D record");
        // Win rate: 10 / (10+5+2) = 10/17 ≈ 58.8%
        assertTrue(rankings.contains("58.8% WR") || rankings.contains("58.9% WR"),
            "Should show win rate percentage");
    }

    @Test
    void handle_handlesZeroBattlesWinRate() {
        // Given: Player with no battles
        PlayerCharacter player = createPlayerWithStats("111111111111111111", 0, 0, 0, 1000);
        when(characterRepository.findTopByElo(eq("222222222222222222"), any(PageRequest.class)))
            .thenReturn(List.of(player));
        when(event.getOption("type")).thenReturn(null);
        when(event.getOption("limit")).thenReturn(null);

        // When: Handle command
        handler.handle(event);

        // Then: Win rate is 0.0%
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field rankingsField = embed.getFields().get(0);
        String rankings = rankingsField.getValue();

        assertTrue(rankings.contains("0.0% WR"), "Should show 0.0% win rate for no battles");
    }

    /**
     * Helper: Create mock players for leaderboard
     */
    private List<PlayerCharacter> createMockPlayers(int count) {
        List<PlayerCharacter> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PlayerCharacter player = PlayerCharacterTestFactory.create(
                "111111111111111" + String.format("%03d", i + 1), "222222222222222222", "Warrior", "Human",
                12, 12, 12, 12, 12, 12
            );
            player.setElo(1500 - (i * 100)); // Decreasing ELO
            player.setWins(10 - i);
            player.setLosses(i);
            player.setDraws(1);
            players.add(player);
        }
        return players;
    }

    /**
     * Helper: Create player with specific stats
     */
    private PlayerCharacter createPlayerWithStats(String userId, int wins, int losses, int draws, int elo) {
        PlayerCharacter player = PlayerCharacterTestFactory.create(
            userId, "222222222222222222", "Warrior", "Human",
            12, 12, 12, 12, 12, 12
        );
        player.setWins(wins);
        player.setLosses(losses);
        player.setDraws(draws);
        player.setElo(elo);
        return player;
    }
}
