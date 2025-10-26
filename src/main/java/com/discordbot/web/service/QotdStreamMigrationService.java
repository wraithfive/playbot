package com.discordbot.web.service;

import com.discordbot.entity.QotdStream;
import com.discordbot.repository.QotdStreamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service to migrate existing QOTD configs to the new multi-stream architecture.
 * Runs once on application startup if old tables exist.
 */
@Service
public class QotdStreamMigrationService {

    private static final Logger log = LoggerFactory.getLogger(QotdStreamMigrationService.class);

    private final QotdStreamRepository streamRepository;
    private final JdbcTemplate jdbcTemplate;

    public QotdStreamMigrationService(
            QotdStreamRepository streamRepository,
            JdbcTemplate jdbcTemplate) {
        this.streamRepository = streamRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateOldConfigsToStreams() {
        // Check if old tables exist
        boolean hasOldConfigTable = checkTableExists("qotd_configs");
        boolean hasOldBannerTable = checkTableExists("qotd_banner");

        if (!hasOldConfigTable) {
            log.info("Old qotd_configs table not found - migration not needed or already completed");
            return;
        }

        log.info("Starting QOTD config → streams migration...");

        try {
            int migratedCount = migrateConfigs(hasOldBannerTable);
            log.info("✓ QOTD migration completed: {} channels migrated to default streams", migratedCount);
        } catch (Exception e) {
            log.error("✗ QOTD migration failed - old tables preserved for manual intervention", e);
            // Don't throw - allow app to start but log the error clearly
        }
    }

    private int migrateConfigs(boolean hasBannerTable) {
        // Query old configs via JDBC (entity classes may not exist for deprecated tables)
        String sql;
        if (hasBannerTable) {
            sql = """
                SELECT qc.guild_id, qc.channel_id, qc.enabled, qc.schedule_cron, qc.timezone,
                       qc.randomize, qc.auto_approve, qc.next_index, qc.last_posted_at,
                       qb.banner_text, qb.embed_color, qb.mention_target
                FROM qotd_configs qc
                LEFT JOIN qotd_banner qb ON qc.channel_id = qb.channel_id
                """;
        } else {
            sql = """
                SELECT guild_id, channel_id, enabled, schedule_cron, timezone,
                       randomize, auto_approve, next_index, last_posted_at,
                       NULL as banner_text, NULL as embed_color, NULL as mention_target
                FROM qotd_configs
                """;
        }

        List<Map<String, Object>> oldConfigs = jdbcTemplate.queryForList(sql);
        int count = 0;

        for (Map<String, Object> row : oldConfigs) {
            String guildId = (String) row.get("guild_id");
            String channelId = (String) row.get("channel_id");

            // Skip if stream already exists (re-run safety)
            if (streamRepository.existsByGuildIdAndChannelId(guildId, channelId)) {
                log.debug("Stream already exists for channel {}, skipping", channelId);
                continue;
            }

            // Create default stream from old config
            QotdStream stream = new QotdStream();
            stream.setGuildId(guildId);
            stream.setChannelId(channelId);
            stream.setStreamName("Default");
            stream.setEnabled((Boolean) row.getOrDefault("enabled", true));
            stream.setScheduleCron((String) row.get("schedule_cron"));
            stream.setTimezone((String) row.getOrDefault("timezone", "UTC"));
            stream.setRandomize((Boolean) row.getOrDefault("randomize", false));
            stream.setAutoApprove((Boolean) row.getOrDefault("auto_approve", false));
            stream.setNextIndex((Integer) row.getOrDefault("next_index", 0));

            // Handle timestamp conversion
            Object lastPostedAtObj = row.get("last_posted_at");
            if (lastPostedAtObj != null) {
                if (lastPostedAtObj instanceof Timestamp) {
                    stream.setLastPostedAt(((Timestamp) lastPostedAtObj).toInstant());
                } else if (lastPostedAtObj instanceof Instant) {
                    stream.setLastPostedAt((Instant) lastPostedAtObj);
                }
            }

            // Banner data (may be null if left join didn't match)
            String bannerText = (String) row.get("banner_text");
            stream.setBannerText(bannerText != null ? bannerText : "❓❓ Question of the Day ❓❓");
            stream.setEmbedColor((Integer) row.get("embed_color"));
            stream.setMentionTarget((String) row.get("mention_target"));

            stream.setCreatedAt(Instant.now());

            QotdStream saved = streamRepository.save(stream);

            // Update questions to reference new stream
            int updatedQuestions = jdbcTemplate.update(
                "UPDATE qotd_questions SET stream_id = ? WHERE guild_id = ? AND channel_id = ?",
                saved.getId(), guildId, channelId
            );

            log.info("Migrated channel {} → stream {} (Default) with {} questions",
                channelId, saved.getId(), updatedQuestions);
            count++;
        }

        return count;
    }

    private boolean checkTableExists(String tableName) {
        try {
            jdbcTemplate.queryForObject(
                "SELECT 1 FROM " + tableName + " LIMIT 1",
                Integer.class
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
