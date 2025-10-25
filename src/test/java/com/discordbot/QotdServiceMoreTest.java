package com.discordbot;

import com.discordbot.entity.QotdConfig;
import com.discordbot.entity.QotdQuestion;
import com.discordbot.repository.QotdConfigRepository;
import com.discordbot.repository.QotdQuestionRepository;
import com.discordbot.web.dto.qotd.QotdDtos;
import com.discordbot.web.service.QotdService;
import com.discordbot.web.service.WebSocketNotificationService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional targeted tests to lift QotdService coverage for corner branches.
 */
class QotdServiceMoreTest {

    private QotdService service;
    private QotdQuestionRepository questionRepo;
    private QotdConfigRepository configRepo;
    private JDA jda;
    private WebSocketNotificationService wsNotificationService;

    @BeforeEach
    void setup() {
        questionRepo = mock(QotdQuestionRepository.class);
        configRepo = mock(QotdConfigRepository.class);
        jda = mock(JDA.class);
        wsNotificationService = mock(WebSocketNotificationService.class);
        service = new QotdService(questionRepo, configRepo, jda, wsNotificationService);
    }

    @Test
    @DisplayName("updateConfig: blank advancedCron falls back to weekly schedule builder")
    void testUpdateConfig_BlankAdvancedCronUsesWeekly() {
        QotdConfig.QotdConfigId id = new QotdConfig.QotdConfigId("guild1", "channel1");
        QotdConfig cfg = new QotdConfig("guild1", "channel1");
        when(configRepo.findById(id)).thenReturn(Optional.of(cfg));
        when(configRepo.save(any(QotdConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        QotdDtos.UpdateConfigRequest req = new QotdDtos.UpdateConfigRequest(
                true, "UTC", "  ", List.of("TUE"), "07:15", false, false
        );

        QotdDtos.QotdConfigDto dto = service.updateConfig("guild1", "channel1", req);
        assertNotNull(dto);
        assertEquals("0 15 7 ? * TUE", dto.scheduleCron(), "Should build weekly cron when advancedCron is blank");
    }

    @Test
    @DisplayName("updateConfig: timezone defaults to UTC when null")
    void testUpdateConfig_TimezoneDefaults() {
        QotdConfig.QotdConfigId id = new QotdConfig.QotdConfigId("guild1", "channel1");
        QotdConfig cfg = new QotdConfig("guild1", "channel1");
        when(configRepo.findById(id)).thenReturn(Optional.of(cfg));
        when(configRepo.save(any(QotdConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        QotdDtos.UpdateConfigRequest req = new QotdDtos.UpdateConfigRequest(
                true, null, null, List.of("MON"), "09:00", false, false
        );

        QotdDtos.QotdConfigDto dto = service.updateConfig("guild1", "channel1", req);
        assertEquals("UTC", dto.timezone());
    }

    @Test
    @DisplayName("getConfig: blank cron yields empty nextRuns")
    void testGetConfig_BlankCronYieldsNoNextRuns() {
        QotdConfig.QotdConfigId id = new QotdConfig.QotdConfigId("guild1", "channel1");
        QotdConfig cfg = new QotdConfig("guild1", "channel1");
        cfg.setEnabled(true);
        cfg.setTimezone("UTC");
        cfg.setScheduleCron("   ");
        when(configRepo.findById(id)).thenReturn(Optional.of(cfg));

        QotdDtos.QotdConfigDto dto = service.getConfig("guild1", "channel1");
        assertNotNull(dto);
        assertTrue(dto.nextRuns().isEmpty());
    }

    @Test
    @DisplayName("postNextQuestion: sequential mode wraps index when out-of-bounds")
    void testPostNextQuestion_SequentialIndexWrapWhenOutOfBounds() {
        QotdConfig.QotdConfigId id = new QotdConfig.QotdConfigId("guild1", "channel1");
        QotdConfig config = new QotdConfig("guild1", "channel1");
        config.setEnabled(true);
        config.setRandomize(false);
        config.setNextIndex(5); // deliberately out of bounds

        QotdQuestion q1 = new QotdQuestion("guild1", "channel1", "Q1");
        setQuestionId(q1, 1001L);
        QotdQuestion q2 = new QotdQuestion("guild1", "channel1", "Q2");
        setQuestionId(q2, 1002L);

        Guild guild = mock(Guild.class);
        TextChannel channel = mock(TextChannel.class);
        MessageCreateAction embedAction = mock(MessageCreateAction.class);

        when(configRepo.findById(id)).thenReturn(Optional.of(config));
        when(questionRepo.findByGuildIdAndChannelIdOrderByDisplayOrderAsc("guild1", "channel1")).thenReturn(List.of(q1, q2));
        when(jda.getGuildById("guild1")).thenReturn(guild);
        when(guild.getTextChannelById("channel1")).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(embedAction);
        when(embedAction.complete()).thenReturn(null);
        when(configRepo.save(any(QotdConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean result = service.postNextQuestion("guild1", "channel1");
        assertTrue(result);
        // Capture saved config to assert next index after removal
        var cfgCaptor = org.mockito.ArgumentCaptor.forClass(QotdConfig.class);
        verify(configRepo).save(cfgCaptor.capture());
        QotdConfig saved = cfgCaptor.getValue();
        assertEquals(0, saved.getNextIndex(), "Index should wrap to 0 and remain 0 after posting first item");
    }

    // Helper to set ID via reflection
    private void setQuestionId(QotdQuestion q, Long id) {
        try {
            java.lang.reflect.Field f = QotdQuestion.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(q, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
