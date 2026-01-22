package com.discordbot;

import com.discordbot.entity.QotdQuestion;
import com.discordbot.entity.QotdStream;
import com.discordbot.repository.QotdQuestionRepository;
import com.discordbot.repository.QotdStreamRepository;
import com.discordbot.web.dto.qotd.QotdDtos.*;
import com.discordbot.web.service.QotdStreamService;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QotdStreamServiceTest {

    private QotdStreamRepository streamRepo;
    private QotdQuestionRepository questionRepo;
    private JDA jda;
    private WebSocketNotificationService wsService;
    private QotdStreamService service;

    @BeforeEach
    void setup() {
        streamRepo = mock(QotdStreamRepository.class);
        questionRepo = mock(QotdQuestionRepository.class);
        jda = mock(JDA.class);
        wsService = mock(WebSocketNotificationService.class);
        service = new QotdStreamService(streamRepo, questionRepo, jda, wsService);
    }

    @Test
    @DisplayName("createStream: enforces 5-stream limit per channel")
    void createStream_enforcesFiveStreamLimit() {
        String guildId = "g1";
        String channelId = "c1";

        // Mock channel validation
        Guild guild = mock(Guild.class);
        TextChannel channel = mock(TextChannel.class);
        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannelById(channelId)).thenReturn(channel);

        // Mock: already 5 streams exist
        when(streamRepo.countByGuildIdAndChannelId(guildId, channelId)).thenReturn(5L);

        CreateStreamRequest request = new CreateStreamRequest(
            "Sixth Stream", true, "UTC", null, null, null, false, false, null, null, null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.createStream(guildId, channelId, request));

        assertTrue(ex.getMessage().contains("Maximum 5 streams"));
    }

    @Test
    @DisplayName("createStream: prevents duplicate stream names in same channel")
    void createStream_preventsDuplicateNames() {
        String guildId = "g1";
        String channelId = "c1";

        // Mock channel validation
        Guild guild = mock(Guild.class);
        TextChannel channel = mock(TextChannel.class);
        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannelById(channelId)).thenReturn(channel);
        when(guild.getThreadChannelById(channelId)).thenReturn(null);

        // Mock: only 2 streams exist
        when(streamRepo.countByGuildIdAndChannelId(guildId, channelId)).thenReturn(2L);

        // Mock: stream with this name already exists
        QotdStream existingStream = new QotdStream();
        existingStream.setStreamName("Daily QOTD");
        when(streamRepo.findByGuildIdAndChannelIdAndStreamName(guildId, channelId, "Daily QOTD"))
            .thenReturn(Optional.of(existingStream));

        CreateStreamRequest request = new CreateStreamRequest(
            "Daily QOTD", true, "UTC", null, null, null, false, false, null, null, null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.createStream(guildId, channelId, request));

        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("createStream: successfully creates stream with valid data")
    void createStream_success() {
        String guildId = "g1";
        String channelId = "c1";

        // Mock channel validation
        Guild guild = mock(Guild.class);
        TextChannel channel = mock(TextChannel.class);
        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannelById(channelId)).thenReturn(channel);
        when(guild.getThreadChannelById(channelId)).thenReturn(null);

        // Mock: only 2 streams exist
        when(streamRepo.countByGuildIdAndChannelId(guildId, channelId)).thenReturn(2L);

        // Mock: no duplicate name
        when(streamRepo.findByGuildIdAndChannelIdAndStreamName(any(), any(), any()))
            .thenReturn(Optional.empty());

        // Mock: save returns stream with ID
        when(streamRepo.save(any())).thenAnswer(inv -> {
            QotdStream stream = inv.getArgument(0);
            stream.setId(123L);
            return stream;
        });

        CreateStreamRequest request = new CreateStreamRequest(
            "Weekly QOTD", true, "America/New_York", "0 0 9 ? * MON",
            null, null, false, false, "🎉 Weekly Question", 0x5865F2, "@everyone"
        );

        QotdStreamDto result = service.createStream(guildId, channelId, request);

        assertNotNull(result);
        assertEquals(123L, result.id());
        assertEquals("Weekly QOTD", result.streamName());
        assertEquals("America/New_York", result.timezone());
        assertEquals("🎉 Weekly Question", result.bannerText());
        assertEquals(0x5865F2, result.embedColor());

        // Verify WebSocket notification
        verify(wsService).notifyQotdStreamChanged(guildId, channelId, 123L, "created");
    }

    @Test
    @DisplayName("deleteStream: removes stream and cascades to questions")
    void deleteStream_cascadeDelete() {
        Long streamId = 123L;

        QotdStream stream = new QotdStream();
        stream.setId(streamId);
        stream.setGuildId("g1");
        stream.setChannelId("c1");
        stream.setStreamName("Test Stream");

        when(streamRepo.findById(streamId)).thenReturn(Optional.of(stream));

        service.deleteStream("g1", streamId);

        verify(streamRepo).deleteById(streamId);
        verify(wsService).notifyQotdStreamChanged("g1", "c1", streamId, "deleted");
    }

    @Test
    @DisplayName("addQuestion: adds question to stream with correct display order")
    void addQuestion_correctDisplayOrder() {
        Long streamId = 123L;

        QotdStream stream = new QotdStream();
        stream.setId(streamId);
        stream.setGuildId("g1");
        stream.setChannelId("c1");

        when(streamRepo.findById(streamId)).thenReturn(Optional.of(stream));

        // Mock: 5 existing questions
        QotdQuestion q1 = new QotdQuestion();
        q1.setDisplayOrder(1);
        QotdQuestion q2 = new QotdQuestion();
        q2.setDisplayOrder(2);
        QotdQuestion q3 = new QotdQuestion();
        q3.setDisplayOrder(3);
        QotdQuestion q4 = new QotdQuestion();
        q4.setDisplayOrder(4);
        QotdQuestion q5 = new QotdQuestion();
        q5.setDisplayOrder(5);

        when(questionRepo.findByStreamIdOrderByDisplayOrderAsc(streamId))
            .thenReturn(Arrays.asList(q1, q2, q3, q4, q5));

        when(questionRepo.save(any())).thenAnswer(inv -> {
            QotdQuestion q = inv.getArgument(0);
            q.setId(999L);
            return q;
        });

        QotdQuestionDto result = service.addQuestion("g1", streamId, "What is your favorite color?");

        assertNotNull(result);
        assertEquals("What is your favorite color?", result.text());

        // Verify the question was saved with display order 6 (next after 5)
        verify(questionRepo).save(argThat(q ->
            q.getStreamId().equals(streamId) &&
            q.getDisplayOrder() == 6 &&
            q.getGuildId().equals("g1") &&
            q.getChannelId().equals("c1")
        ));
    }

    @Test
    @DisplayName("listStreams: returns streams ordered by creation")
    void listStreams_orderedByCreation() {
        String guildId = "g1";
        String channelId = "c1";

        // Mock channel validation
        Guild guild = mock(Guild.class);
        TextChannel channel = mock(TextChannel.class);
        when(jda.getGuildById(guildId)).thenReturn(guild);
        when(guild.getTextChannelById(channelId)).thenReturn(channel);
        when(guild.getThreadChannelById(channelId)).thenReturn(null);

        QotdStream stream1 = new QotdStream();
        stream1.setId(1L);
        stream1.setStreamName("Default");
        stream1.setGuildId(guildId);
        stream1.setChannelId(channelId);
        stream1.setCreatedAt(Instant.now());

        QotdStream stream2 = new QotdStream();
        stream2.setId(2L);
        stream2.setStreamName("Weekly");
        stream2.setGuildId(guildId);
        stream2.setChannelId(channelId);
        stream2.setCreatedAt(Instant.now());

        when(streamRepo.findByGuildIdAndChannelIdOrderByIdAsc(guildId, channelId))
            .thenReturn(Arrays.asList(stream1, stream2));

        List<QotdStreamDto> result = service.listStreams(guildId, channelId);

        assertEquals(2, result.size());
        assertEquals("Default", result.get(0).streamName());
        assertEquals("Weekly", result.get(1).streamName());
    }

    @Test
    @DisplayName("updateStream: prevents name conflicts")
    void updateStream_preventNameConflicts() {
        Long streamId = 1L;
        String guildId = "g1";
        String channelId = "c1";

        QotdStream existingStream = new QotdStream();
        existingStream.setId(streamId);
        existingStream.setGuildId(guildId);
        existingStream.setChannelId(channelId);
        existingStream.setStreamName("Old Name");

        when(streamRepo.findById(streamId)).thenReturn(Optional.of(existingStream));

        // Mock: another stream with the target name already exists
        QotdStream conflictingStream = new QotdStream();
        conflictingStream.setId(2L);
        conflictingStream.setStreamName("New Name");

        when(streamRepo.findByGuildIdAndChannelIdAndStreamName(guildId, channelId, "New Name"))
            .thenReturn(Optional.of(conflictingStream));

        UpdateStreamRequest request = new UpdateStreamRequest(
            "New Name", true, "UTC", null, null, null, false, false
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.updateStream("g1", streamId, request));

        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("validateChannelBelongsToGuild: throws when guild not found")
    void validateChannel_guildNotFound() {
        when(jda.getGuildById("g1")).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.listStreams("g1", "c1"));

        assertTrue(ex.getMessage().contains("Guild not found"));
    }

    @Test
    @DisplayName("validateChannelBelongsToGuild: throws when channel not in guild")
    void validateChannel_channelNotInGuild() {
        Guild guild = mock(Guild.class);
        when(jda.getGuildById("g1")).thenReturn(guild);
        when(guild.getTextChannelById("c1")).thenReturn(null);
        when(guild.getThreadChannelById("c1")).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.listStreams("g1", "c1"));

        assertTrue(ex.getMessage().contains("Channel not found"));
    }
}
