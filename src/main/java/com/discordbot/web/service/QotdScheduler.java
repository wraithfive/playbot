package com.discordbot.web.service;

import com.discordbot.entity.QotdStream;
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
 * Supports stream-based scheduling only (legacy config-based system deprecated).
 */
@Component
public class QotdScheduler {
    private static final Logger logger = LoggerFactory.getLogger(QotdScheduler.class);

    // Stream-based scheduling
    private final QotdStreamRepository streamRepository;
    private final QotdStreamService streamService;

    public QotdScheduler(
            QotdStreamRepository streamRepository,
            QotdStreamService streamService) {
        this.streamRepository = streamRepository;
        this.streamService = streamService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        tickStreams();
    }

    /**
     * Process stream-based QOTD schedules.
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
}
