package com.discordbot.web.service;

import com.discordbot.entity.QotdConfig;
import com.discordbot.entity.QotdStream;
import com.discordbot.repository.QotdConfigRepository;
import com.discordbot.repository.QotdStreamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Scheduler for QOTD posting.
 * Now supports both legacy configs (deprecated) and new stream-based scheduling.
 */
@Component
@SuppressWarnings("deprecation") // Uses legacy QotdConfig for backward compatibility during migration
public class QotdScheduler {
    private static final Logger logger = LoggerFactory.getLogger(QotdScheduler.class);

    // Legacy (deprecated) - keep for backward compatibility during migration
    private final QotdConfigRepository configRepo;
    private final QotdService qotdService;

    // NEW: Stream-based scheduling
    private final QotdStreamRepository streamRepository;
    private final QotdStreamService streamService;

    public QotdScheduler(
            QotdConfigRepository configRepo,
            QotdService qotdService,
            QotdStreamRepository streamRepository,
            QotdStreamService streamService) {
        this.configRepo = configRepo;
        this.qotdService = qotdService;
        this.streamRepository = streamRepository;
        this.streamService = streamService;
    }

    // Check every minute whether any channel is due for a QOTD post
    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        // NEW: Process stream-based schedules (primary path)
        tickStreams();

        // LEGACY: Process old config-based schedules (deprecated, for backward compatibility)
        // TODO: Remove this after migration is complete and verified
        tickLegacyConfigs();
    }

    /**
     * NEW: Process stream-based QOTD schedules.
     * Each stream can have independent schedules within the same channel.
     */
    private void tickStreams() {
        List<QotdStream> streams = streamRepository.findByEnabledTrue();

        for (QotdStream stream : streams) {
            if (stream.getScheduleCron() == null || stream.getScheduleCron().isBlank()) {
                continue;
            }

            try {
                if (shouldPostNow(stream)) {
                    logger.info("QOTD due for stream {} ({}) in channel {} at {}",
                            stream.getId(), stream.getStreamName(), stream.getChannelId(), ZonedDateTime.now());
                    streamService.postNextQuestion(stream.getId());
                }
            } catch (Exception e) {
                logger.error("Failed to process stream {}: {}", stream.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Check if a stream should post now based on cron schedule and last posted time.
     */
    private boolean shouldPostNow(QotdStream stream) {
        try {
            CronExpression expr = CronExpression.parse(stream.getScheduleCron());
            ZoneId zone = ZoneId.of(stream.getTimezone() != null ? stream.getTimezone() : "UTC");
            ZonedDateTime now = ZonedDateTime.now(zone).withSecond(0).withNano(0);
            ZonedDateTime last = now.minusMinutes(1);
            ZonedDateTime nextAfterLast = expr.next(last);

            if (nextAfterLast == null || nextAfterLast.isAfter(now)) {
                return false; // Not due yet
            }

            // Prevent duplicate posts within 2-minute window
            if (stream.getLastPostedAt() != null) {
                ZonedDateTime lastPosted = stream.getLastPostedAt().atZone(zone);
                if (lastPosted.isAfter(now.minusMinutes(2))) {
                    logger.debug("Stream {} already posted recently, skipping", stream.getId());
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            logger.warn("Invalid cron for stream {}: {}", stream.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * LEGACY: Process old config-based schedules.
     * TODO: Remove this method after migration is complete and all channels use streams.
     */
    @Deprecated
    private void tickLegacyConfigs() {
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
                    logger.info("LEGACY QOTD due for channel {} in guild {} at {}", cfg.getChannelId(), cfg.getGuildId(), now);
                    qotdService.postNextQuestion(cfg.getGuildId(), cfg.getChannelId());
                }
            } catch (Exception e) {
                logger.warn("Invalid cron for channel {} in guild {}: {}", cfg.getChannelId(), cfg.getGuildId(), e.getMessage());
            }
        }
    }
}
