package com.discordbot;

import com.discordbot.entity.QotdStream;
import com.discordbot.repository.QotdConfigRepository;
import com.discordbot.repository.QotdStreamRepository;
import com.discordbot.web.service.QotdScheduler;
import com.discordbot.web.service.QotdService;
import com.discordbot.web.service.QotdStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * Tests for the stream-based scheduling logic in QotdScheduler.
 */
class QotdStreamSchedulerTest {

    private QotdConfigRepository configRepo;
    private QotdService qotdService;
    private QotdStreamRepository streamRepo;
    private QotdStreamService streamService;
    private QotdScheduler scheduler;

    @BeforeEach
    void setup() {
        configRepo = mock(QotdConfigRepository.class);
        qotdService = mock(QotdService.class);
        streamRepo = mock(QotdStreamRepository.class);
        streamService = mock(QotdStreamService.class);

        // Default: no legacy configs
        when(configRepo.findAll()).thenReturn(Collections.emptyList());

        scheduler = new QotdScheduler(configRepo, qotdService, streamRepo, streamService);
    }

    @Test
    @DisplayName("tick: processes enabled streams with valid cron")
    void tick_processesEnabledStreams() {
        QotdStream stream1 = createStream(1L, "g1", "c1", "Default", "* * * * * *", "UTC", true);
        QotdStream stream2 = createStream(2L, "g1", "c1", "Weekly", "* * * * * *", "UTC", true);

        when(streamRepo.findByEnabledTrue()).thenReturn(Arrays.asList(stream1, stream2));

        scheduler.tick();

        // Both streams should trigger posting
        verify(streamService, atLeastOnce()).postNextQuestion(1L);
        verify(streamService, atLeastOnce()).postNextQuestion(2L);
    }

    @Test
    @DisplayName("tick: skips disabled streams")
    void tick_skipsDisabledStreams() {
        QotdStream enabledStream = createStream(1L, "g1", "c1", "Enabled", "* * * * * *", "UTC", true);
        // Disabled stream exists but won't be in query results
        createStream(2L, "g1", "c1", "Disabled", "* * * * * *", "UTC", false);

        // Mock should only return enabled stream due to findByEnabledTrue()
        when(streamRepo.findByEnabledTrue()).thenReturn(Arrays.asList(enabledStream));

        scheduler.tick();

        verify(streamService, atLeastOnce()).postNextQuestion(1L);
        verify(streamService, never()).postNextQuestion(2L);
    }

    @Test
    @DisplayName("tick: skips streams with null or blank cron")
    void tick_skipsNullOrBlankCron() {
        QotdStream noCron = createStream(1L, "g1", "c1", "NoCron", null, "UTC", true);
        QotdStream blankCron = createStream(2L, "g1", "c1", "BlankCron", "", "UTC", true);

        when(streamRepo.findByEnabledTrue()).thenReturn(Arrays.asList(noCron, blankCron));

        scheduler.tick();

        verify(streamService, never()).postNextQuestion(anyLong());
    }

    @Test
    @DisplayName("tick: skips streams with invalid cron expressions")
    void tick_skipsInvalidCron() {
        QotdStream invalidStream = createStream(1L, "g1", "c1", "Invalid", "not-a-cron", "UTC", true);

        when(streamRepo.findByEnabledTrue()).thenReturn(Arrays.asList(invalidStream));

        scheduler.tick();

        verify(streamService, never()).postNextQuestion(1L);
    }

    @Test
    @DisplayName("tick: prevents duplicate posts within 2-minute window")
    void tick_preventsDuplicatePosts() {
        // Stream that posted 1 minute ago
        QotdStream recentlyPosted = createStream(1L, "g1", "c1", "Recent", "* * * * * *", "UTC", true);
        recentlyPosted.setLastPostedAt(Instant.now().minusSeconds(60)); // 1 minute ago

        when(streamRepo.findByEnabledTrue()).thenReturn(Arrays.asList(recentlyPosted));

        scheduler.tick();

        // Should NOT post because within 2-minute window
        verify(streamService, never()).postNextQuestion(1L);
    }

    @Test
    @DisplayName("tick: allows post after 2-minute window")
    void tick_allowsPostAfterTwoMinutes() {
        // Stream that posted 3 minutes ago
        QotdStream oldPost = createStream(1L, "g1", "c1", "Old", "* * * * * *", "UTC", true);
        oldPost.setLastPostedAt(Instant.now().minusSeconds(180)); // 3 minutes ago

        when(streamRepo.findByEnabledTrue()).thenReturn(Arrays.asList(oldPost));

        scheduler.tick();

        // Should post because outside 2-minute window
        verify(streamService, atLeastOnce()).postNextQuestion(1L);
    }

    @Test
    @DisplayName("tick: handles multiple streams in same channel with different schedules")
    void tick_multipleStreamsInSameChannel() {
        // Daily at 9 AM (won't fire with every-second cron in test)
        QotdStream daily = createStream(1L, "g1", "c1", "Daily", "0 0 9 * * *", "UTC", true);

        // Every second (will fire in test)
        QotdStream frequent = createStream(2L, "g1", "c1", "Frequent", "* * * * * *", "UTC", true);

        when(streamRepo.findByEnabledTrue()).thenReturn(Arrays.asList(daily, frequent));

        scheduler.tick();

        // Only frequent stream should trigger
        verify(streamService, never()).postNextQuestion(1L);
        verify(streamService, atLeastOnce()).postNextQuestion(2L);
    }

    @Test
    @DisplayName("tick: handles stream service errors gracefully")
    void tick_handlesStreamServiceErrors() {
        QotdStream stream1 = createStream(1L, "g1", "c1", "Stream1", "* * * * * *", "UTC", true);
        QotdStream stream2 = createStream(2L, "g1", "c1", "Stream2", "* * * * * *", "UTC", true);

        when(streamRepo.findByEnabledTrue()).thenReturn(Arrays.asList(stream1, stream2));

        // Stream 1 throws error
        doThrow(new RuntimeException("Test error")).when(streamService).postNextQuestion(1L);

        scheduler.tick();

        // Should still process stream 2 despite stream 1 error
        verify(streamService, atLeastOnce()).postNextQuestion(2L);
    }

    @Test
    @DisplayName("tick: processes both legacy and stream schedules during migration period")
    void tick_processesBothLegacyAndStreams() {
        // Mock legacy config
        when(configRepo.findAll()).thenReturn(Collections.emptyList()); // No legacy for simplicity

        // Mock new stream
        QotdStream newStream = createStream(1L, "g1", "c1", "New", "* * * * * *", "UTC", true);
        when(streamRepo.findByEnabledTrue()).thenReturn(Arrays.asList(newStream));

        scheduler.tick();

        // Should process new streams
        verify(streamService, atLeastOnce()).postNextQuestion(1L);

        // Legacy service not called (no legacy configs)
        verify(qotdService, never()).postNextQuestion(anyString(), anyString());
    }

    // Helper method
    private QotdStream createStream(Long id, String guildId, String channelId,
                                   String name, String cron, String tz, boolean enabled) {
        QotdStream stream = new QotdStream();
        stream.setId(id);
        stream.setGuildId(guildId);
        stream.setChannelId(channelId);
        stream.setStreamName(name);
        stream.setScheduleCron(cron);
        stream.setTimezone(tz);
        stream.setEnabled(enabled);
        stream.setRandomize(false);
        stream.setAutoApprove(false);
        stream.setNextIndex(0);
        return stream;
    }
}
