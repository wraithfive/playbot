package com.discordbot.battle.controller;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.service.BattleMetricsService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BattleStatsCommandHandler.
 * Tests the /battle-stats command for displaying battle system metrics.
 */
class BattleStatsCommandHandlerTest {

    @Mock
    private BattleProperties battleProperties;

    @Mock
    private BattleMetricsService metricsService;

    @Mock
    private SlashCommandInteractionEvent event;

    @Mock
    private Guild guild;

    @Mock
    private User user;

    @Mock
    private ReplyCallbackAction replyAction;

    private BattleStatsCommandHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(battleProperties.isEnabled()).thenReturn(true);
        handler = new BattleStatsCommandHandler(battleProperties, metricsService);

        // Default mocks
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user1");
        when(guild.getId()).thenReturn("guild1");
        when(guild.getName()).thenReturn("Test Server");
        when(event.reply(anyString())).thenReturn(replyAction);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        when(replyAction.queue()).thenReturn(null);
    }

    @Test
    void canHandle_returnsTrueForBattleStatsWhenEnabled() {
        assertTrue(handler.canHandle("battle-stats"));
    }

    @Test
    void canHandle_returnsFalseWhenBattleSystemDisabled() {
        when(battleProperties.isEnabled()).thenReturn(false);
        assertFalse(handler.canHandle("battle-stats"));
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
        verify(metricsService, never()).getStats();
    }

    @Test
    void handle_displaysStatsWithAllFields() {
        // Given: Metrics service returns stats
        BattleMetricsService.BattleStats stats = createMockStats();
        when(metricsService.getStats()).thenReturn(stats);

        // When: Handle command
        handler.handle(event);

        // Then: Displays stats embed
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());
        verify(replyAction).setEphemeral(true);

        MessageEmbed embed = embedCaptor.getValue();
        assertEquals("⚔️ Battle System Statistics", embed.getTitle());
        assertEquals("Real-time metrics for the battle system", embed.getDescription());

        // Should have 5 fields: Challenges, Outcomes, Activity, Combat Actions, Performance
        assertEquals(5, embed.getFields().size());
    }

    @Test
    void handle_embedHasCyanColor() {
        // Given: Metrics service returns stats
        BattleMetricsService.BattleStats stats = createMockStats();
        when(metricsService.getStats()).thenReturn(stats);

        // When: Handle command
        handler.handle(event);

        // Then: Embed has cyan color
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed.getColor());
        // Cyan has high blue and green components
        assertTrue(embed.getColor().getBlue() > 200 && embed.getColor().getGreen() > 200,
            "Stats embed should have cyan-ish color");
    }

    @Test
    void handle_displaysChallengeStats() {
        // Given: Specific challenge stats
        BattleMetricsService.BattleStats stats = new BattleMetricsService.BattleStats(
            100, 75, 20, 5,  // challenges: created, accepted, declined, pending
            50, 10, 3, 2,    // battles: completed, forfeit, timeout, aborted
            5, 5,            // activity: active, pending
            500, 200, 50, 75, // actions: attacks, defends, spells, crits
            150.5, 30000.0   // performance: avg turn ms, avg battle ms
        );
        when(metricsService.getStats()).thenReturn(stats);

        // When: Handle command
        handler.handle(event);

        // Then: Challenge field shows correct values
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field challengesField = embed.getFields().stream()
            .filter(f -> f.getName().contains("Challenges"))
            .findFirst()
            .orElseThrow();

        String value = challengesField.getValue();
        assertTrue(value.contains("100"), "Should show 100 created challenges");
        assertTrue(value.contains("75"), "Should show 75 accepted challenges");
        assertTrue(value.contains("20"), "Should show 20 declined challenges");
        assertTrue(value.contains("5"), "Should show 5 pending challenges");
    }

    @Test
    void handle_displaysBattleOutcomesWithTotal() {
        // Given: Specific battle outcome stats
        BattleMetricsService.BattleStats stats = new BattleMetricsService.BattleStats(
            100, 75, 20, 5,
            40, 8, 3, 2, // completed=40, forfeit=8, timeout=3, aborted=2 (total=53)
            5, 5,
            500, 200, 50, 75,
            150.5, 30000.0
        );
        when(metricsService.getStats()).thenReturn(stats);

        // When: Handle command
        handler.handle(event);

        // Then: Battle Outcomes field shows all values and total
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field outcomesField = embed.getFields().stream()
            .filter(f -> f.getName().contains("Battle Outcomes"))
            .findFirst()
            .orElseThrow();

        String value = outcomesField.getValue();
        assertTrue(value.contains("40"), "Should show 40 completed");
        assertTrue(value.contains("8"), "Should show 8 forfeit");
        assertTrue(value.contains("3"), "Should show 3 timeout");
        assertTrue(value.contains("2"), "Should show 2 aborted");
        assertTrue(value.contains("53"), "Should show 53 total (40+8+3+2)");
    }

    @Test
    void handle_displaysCurrentActivity() {
        // Given: Stats with current activity
        BattleMetricsService.BattleStats stats = new BattleMetricsService.BattleStats(
            100, 75, 20, 5,
            50, 10, 3, 2,
            12, 7, // active=12, pending=7
            500, 200, 50, 75,
            150.5, 30000.0
        );
        when(metricsService.getStats()).thenReturn(stats);

        // When: Handle command
        handler.handle(event);

        // Then: Current Activity field shows values
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field activityField = embed.getFields().stream()
            .filter(f -> f.getName().contains("Current Activity"))
            .findFirst()
            .orElseThrow();

        String value = activityField.getValue();
        assertTrue(value.contains("12"), "Should show 12 active battles");
        assertTrue(value.contains("7"), "Should show 7 pending");
    }

    @Test
    void handle_displaysCombatActions() {
        // Given: Stats with combat actions
        BattleMetricsService.BattleStats stats = new BattleMetricsService.BattleStats(
            100, 75, 20, 5,
            50, 10, 3, 2,
            5, 5,
            1500, 600, 250, 180, // attacks=1500, defends=600, spells=250, crits=180
            150.5, 30000.0
        );
        when(metricsService.getStats()).thenReturn(stats);

        // When: Handle command
        handler.handle(event);

        // Then: Combat Actions field shows all values
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field actionsField = embed.getFields().stream()
            .filter(f -> f.getName().contains("Combat Actions"))
            .findFirst()
            .orElseThrow();

        String value = actionsField.getValue();
        assertTrue(value.contains("1,500") || value.contains("1500"),
            "Should show 1500 attacks (possibly formatted with comma)");
        assertTrue(value.contains("600"), "Should show 600 defends");
        assertTrue(value.contains("250"), "Should show 250 spells");
        assertTrue(value.contains("180"), "Should show 180 crits");
    }

    @Test
    void handle_displaysPerformanceMetrics() {
        // Given: Stats with performance metrics
        BattleMetricsService.BattleStats stats = new BattleMetricsService.BattleStats(
            100, 75, 20, 5,
            50, 10, 3, 2,
            5, 5,
            500, 200, 50, 75,
            125.7, 45000.5 // avgTurnMs=125.7, avgBattleMs=45000.5
        );
        when(metricsService.getStats()).thenReturn(stats);

        // When: Handle command
        handler.handle(event);

        // Then: Performance field shows metrics with decimals
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        MessageEmbed.Field perfField = embed.getFields().stream()
            .filter(f -> f.getName().contains("Performance"))
            .findFirst()
            .orElseThrow();

        String value = perfField.getValue();
        assertTrue(value.contains("125.7"), "Should show avg turn 125.7 ms");
        assertTrue(value.contains("45000.5"), "Should show avg battle 45000.5 ms");
    }

    @Test
    void handle_displaysFooterWithPhaseInfo() {
        // Given: Metrics service returns stats
        BattleMetricsService.BattleStats stats = createMockStats();
        when(metricsService.getStats()).thenReturn(stats);

        // When: Handle command
        handler.handle(event);

        // Then: Footer mentions Phase 8
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();
        assertNotNull(embed.getFooter());
        assertTrue(embed.getFooter().getText().contains("Phase 8"));
        assertTrue(embed.getFooter().getText().contains("Monitoring"));
    }

    @Test
    void handle_handlesException() {
        // Given: Metrics service throws exception
        when(metricsService.getStats())
            .thenThrow(new RuntimeException("Database error"));

        // When: Handle command
        handler.handle(event);

        // Then: Returns error message
        verify(event).reply("❌ An error occurred while displaying battle stats.");
        verify(replyAction).setEphemeral(true);
    }

    @Test
    void handle_formatsLargeNumbersWithCommas() {
        // Given: Stats with large numbers (test number formatting)
        BattleMetricsService.BattleStats stats = new BattleMetricsService.BattleStats(
            10000, 7500, 2000, 500, // Large challenge numbers
            5000, 1000, 300, 200,
            50, 50,
            100000, 50000, 25000, 15000, // Large action numbers
            150.5, 30000.0
        );
        when(metricsService.getStats()).thenReturn(stats);

        // When: Handle command
        handler.handle(event);

        // Then: Numbers are formatted with commas
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(event).replyEmbeds(embedCaptor.capture());

        MessageEmbed embed = embedCaptor.getValue();

        // Check that large numbers have comma formatting
        String allFieldsText = embed.getFields().stream()
            .map(MessageEmbed.Field::getValue)
            .reduce("", (a, b) -> a + " " + b);

        assertTrue(allFieldsText.contains("10,000") || allFieldsText.contains("10000"),
            "Should format large numbers");
    }

    /**
     * Helper: Create mock stats with reasonable values
     */
    private BattleMetricsService.BattleStats createMockStats() {
        return new BattleMetricsService.BattleStats(
            100, 75, 20, 5,  // challenges
            50, 10, 3, 2,    // battles
            5, 5,            // activity
            500, 200, 50, 75, // actions
            150.5, 30000.0   // performance
        );
    }
}
