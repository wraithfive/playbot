package com.discordbot.web.service;

import com.discordbot.entity.QotdConfig;
import com.discordbot.repository.QotdConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
public class QotdScheduler {
    private static final Logger logger = LoggerFactory.getLogger(QotdScheduler.class);
    private final QotdConfigRepository configRepo;
    private final QotdService qotdService;

    public QotdScheduler(QotdConfigRepository configRepo, QotdService qotdService) {
        this.configRepo = configRepo;
        this.qotdService = qotdService;
    }

    // Check every minute whether any channel is due for a QOTD post
    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        List<QotdConfig> configs = configRepo.findAll();
        for (QotdConfig cfg : configs) {
            if (!cfg.isEnabled()) continue;
            String cron = cfg.getScheduleCron();
            if (cron == null || cron.isBlank()) continue;
            try {
                CronExpression expr = CronExpression.parse(cron);
                ZoneId zone = ZoneId.of(cfg.getTimezone() == null ? "UTC" : cfg.getTimezone());
                ZonedDateTime now = ZonedDateTime.now(zone).withSecond(0).withNano(0);
                ZonedDateTime last = now.minusMinutes(1);
                ZonedDateTime nextAfterLast = expr.next(last);
                if (nextAfterLast != null && !nextAfterLast.isAfter(now)) {
                    // Check if we already posted in this time window to prevent duplicates
                    if (cfg.getLastPostedAt() != null) {
                        ZonedDateTime lastPosted = cfg.getLastPostedAt().atZone(zone);
                        // If we posted within the last 2 minutes, skip this trigger
                        if (lastPosted.isAfter(now.minusMinutes(2))) {
                            logger.debug("QOTD already posted recently for channel {} in guild {}, skipping", cfg.getChannelId(), cfg.getGuildId());
                            continue;
                        }
                    }
                    logger.info("QOTD due for channel {} in guild {} at {}", cfg.getChannelId(), cfg.getGuildId(), now);
                    qotdService.postNextQuestion(cfg.getGuildId(), cfg.getChannelId());
                }
            } catch (Exception e) {
                logger.warn("Invalid cron for channel {} in guild {}: {}", cfg.getChannelId(), cfg.getGuildId(), e.getMessage());
            }
        }
    }
}
