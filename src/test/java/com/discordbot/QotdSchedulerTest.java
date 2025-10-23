package com.discordbot;

import com.discordbot.entity.QotdConfig;
import com.discordbot.repository.QotdConfigRepository;
import com.discordbot.web.service.QotdScheduler;
import com.discordbot.web.service.QotdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class QotdSchedulerTest {

    private QotdConfigRepository repo;
    private QotdService service;
    private QotdScheduler scheduler;

    @BeforeEach
    void setup() {
        repo = mock(QotdConfigRepository.class);
        service = mock(QotdService.class);
        scheduler = new QotdScheduler(repo, service);
    }

    @Test
    @DisplayName("tick posts next question when cron is due")
    void tick_triggers() {
        QotdConfig cfg = new QotdConfig("g1", "c1");
        cfg.setEnabled(true);
        cfg.setTimezone("UTC");
        // Run at top of every minute; tick() compares next from last minute to now
        cfg.setScheduleCron("0 * * * * *");
        when(repo.findAll()).thenReturn(List.of(cfg));

        scheduler.tick();

        verify(service, atLeastOnce()).postNextQuestion("g1", "c1");
    }

    @Test
    @DisplayName("tick skips when disabled or invalid cron")
    void tick_skips() {
        QotdConfig disabled = new QotdConfig("g1", "c1");
        disabled.setEnabled(false);
        QotdConfig invalid = new QotdConfig("g2", "c2");
        invalid.setEnabled(true);
        invalid.setScheduleCron("not-a-cron");
        when(repo.findAll()).thenReturn(List.of(disabled, invalid));

        scheduler.tick();

        verify(service, never()).postNextQuestion(anyString(), anyString());
    }
}
