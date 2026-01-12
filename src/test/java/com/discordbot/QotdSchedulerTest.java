package com.discordbot;

import com.discordbot.entity.QotdConfig;
import com.discordbot.repository.QotdConfigRepository;
import com.discordbot.repository.QotdStreamRepository;
import com.discordbot.web.service.QotdScheduler;
import com.discordbot.web.service.QotdService;
import com.discordbot.web.service.QotdStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@SuppressWarnings({"deprecation", "null"})
class QotdSchedulerTest {

    private QotdConfigRepository repo;
    private QotdService qotd;
    private QotdStreamRepository streamRepo;
    private QotdStreamService streamService;
    private QotdScheduler scheduler;

    @BeforeEach
    void setup() {
        repo = mock(QotdConfigRepository.class);
        qotd = mock(QotdService.class);
        streamRepo = mock(QotdStreamRepository.class);
        streamService = mock(QotdStreamService.class);

        // Mock stream repository to return empty list by default (streams tested separately)
        when(streamRepo.findByEnabledTrue()).thenReturn(Collections.emptyList());

        scheduler = new QotdScheduler(repo, qotd, streamRepo, streamService);
    }

    @Test
    @DisplayName("tick: triggers post when due with valid cron and enabled config")
    void tick_triggersPostWhenDue() {
        QotdConfig cfg = new QotdConfig("g1", "c1");
        cfg.setEnabled(true);
        cfg.setScheduleCron("* * * * * *"); // every second
        cfg.setTimezone("UTC");
        cfg.setLastPostedAt(null);

        when(repo.findAll()).thenReturn(List.of(cfg));

        scheduler.tick();
        verify(qotd, atLeastOnce()).postNextQuestion("g1", "c1");
    }

    @Test
    @DisplayName("tick: skips when cron is invalid")
    void tick_skipsInvalidCron() {
        QotdConfig cfg = new QotdConfig("g1", "c1");
        cfg.setEnabled(true);
        cfg.setScheduleCron("not-a-cron");
        cfg.setTimezone("UTC");

        when(repo.findAll()).thenReturn(List.of(cfg));

        scheduler.tick();
        verify(qotd, never()).postNextQuestion(anyString(), anyString());
    }

    @Test
    @DisplayName("tick: skips when disabled or blank cron")
    void tick_skipsDisabledOrBlank() {
        QotdConfig disabled = new QotdConfig("g1", "c1");
        disabled.setEnabled(false);
        disabled.setScheduleCron("* * * * * *");

        QotdConfig blankCron = new QotdConfig("g2", "c2");
        blankCron.setEnabled(true);
        blankCron.setScheduleCron("");

        when(repo.findAll()).thenReturn(List.of(disabled, blankCron));

        scheduler.tick();
        verify(qotd, never()).postNextQuestion(anyString(), anyString());
    }

    @Test
    @DisplayName("tick: skips when already posted recently in time window")
    void tick_skipsAlreadyPostedRecently() {
        QotdConfig cfg = new QotdConfig("g1", "c1");
        cfg.setEnabled(true);
        cfg.setScheduleCron("* * * * * *");
        cfg.setTimezone("UTC");
        cfg.setLastPostedAt(Instant.now()); // within last 2 minutes

        when(repo.findAll()).thenReturn(List.of(cfg));

        scheduler.tick();
        verify(qotd, never()).postNextQuestion(anyString(), anyString());
    }
}

