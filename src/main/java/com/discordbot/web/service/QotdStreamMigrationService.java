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
        // Check if old tables exist (either old name or deprecated name)
        // The deprecated name is used after migration 008 renames the tables
        boolean hasOldConfigTable = checkTableExists("qotd_configs") || checkTableExists("qotd_configs_deprecated");
        boolean hasOldBannerTable = checkTableExists("qotd_banner") || checkTableExists("qotd_banner_deprecated");

        String configTableName = checkTableExists("qotd_configs") ? "qotd_configs" : "qotd_configs_deprecated";
        String bannerTableName = checkTableExists("qotd_banner") ? "qotd_banner" : "qotd_banner_deprecated";

        if (!hasOldConfigTable) {
            log.info("Old qotd_configs table not found - migration not needed or already completed");
            return;
        }

        log.info("Starting QOTD config → streams migration from table: {}", configTableName);

        try {
            int migratedCount = migrateConfigs(hasOldBannerTable, configTableName, bannerTableName);
            log.info("✓ QOTD migration completed: {} channels migrated to default streams", migratedCount);

            // Also fix any orphaned questions (questions without stream_id)
            int orphanedFixed = fixOrphanedQuestions();
            if (orphanedFixed > 0) {
                log.info("✓ Fixed {} orphaned questions by assigning to Default streams", orphanedFixed);
            }
        } catch (Exception e) {
            log.error("✗ QOTD migration failed - old tables preserved for manual intervention", e);
            // Don't throw - allow app to start but log the error clearly
        }
    }

    private int migrateConfigs(boolean hasBannerTable, String configTableName, String bannerTableName) {
        // Query old configs via JDBC (entity classes may not exist for deprecated tables)
        String sql;
        if (hasBannerTable) {
            sql = String.format("""
                SELECT qc.guild_id, qc.channel_id, qc.enabled, qc.schedule_cron, qc.timezone,
                       qc.randomize, qc.auto_approve, qc.next_index, qc.last_posted_at,
                       qb.banner_text, qb.embed_color, qb.mention_target
                FROM %s qc
                LEFT JOIN %s qb ON qc.channel_id = qb.channel_id
                """, configTableName, bannerTableName);
        } else {
            sql = String.format("""
                SELECT guild_id, channel_id, enabled, schedule_cron, timezone,
                       randomize, auto_approve, next_index, last_posted_at,
                       NULL as banner_text, NULL as embed_color, NULL as mention_target
                FROM %s
                """, configTableName);
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

            // Check how many questions exist for this channel BEFORE migration
            Integer totalQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM qotd_questions WHERE guild_id = ? AND channel_id = ?",
                Integer.class, guildId, channelId
            );

            Integer unmappedQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM qotd_questions WHERE guild_id = ? AND channel_id = ? AND stream_id IS NULL",
                Integer.class, guildId, channelId
            );

            log.info("Channel {} has {} total questions, {} without stream_id",
                channelId, totalQuestions, unmappedQuestions);

            // Update questions to reference new stream (only questions not already assigned to a stream)
            int updatedQuestions = jdbcTemplate.update(
                "UPDATE qotd_questions SET stream_id = ? WHERE guild_id = ? AND channel_id = ? AND stream_id IS NULL",
                saved.getId(), guildId, channelId
            );

            log.info("Migrated channel {} → stream {} (Default) with {} questions updated",
                channelId, saved.getId(), updatedQuestions);
            count++;
        }

        return count;
    }

    /**
     * Fix orphaned questions (questions without stream_id) by assigning them to their channel's Default stream.
     * This handles questions that were created after streams were introduced but before the question creation
     * code was updated to require stream_id.
     */
    private int fixOrphanedQuestions() {
        log.info("Checking for orphaned questions (questions without stream_id)...");

        // Count orphaned questions first
        Integer orphanedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM qotd_questions WHERE stream_id IS NULL AND guild_id IS NOT NULL AND channel_id IS NOT NULL",
            Integer.class
        );

        if (orphanedCount == null || orphanedCount == 0) {
            log.info("No orphaned questions found");
            return 0;
        }

        log.info("Found {} orphaned questions, assigning to Default streams...", orphanedCount);

        // Assign orphaned questions to their channel's Default stream
        int updated = jdbcTemplate.update("""
            UPDATE qotd_questions q
            SET stream_id = (
                SELECT s.id
                FROM qotd_streams s
                WHERE s.guild_id = q.guild_id
                  AND s.channel_id = q.channel_id
                  AND s.stream_name = 'Default'
                LIMIT 1
            )
            WHERE q.stream_id IS NULL
              AND q.guild_id IS NOT NULL
              AND q.channel_id IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM qotd_streams s
                  WHERE s.guild_id = q.guild_id
                    AND s.channel_id = q.channel_id
                    AND s.stream_name = 'Default'
              )
            """);

        // Check if any questions couldn't be assigned (no Default stream exists)
        Integer stillOrphaned = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM qotd_questions WHERE stream_id IS NULL AND guild_id IS NOT NULL AND channel_id IS NOT NULL",
            Integer.class
        );

        if (stillOrphaned != null && stillOrphaned > 0) {
            log.warn("{} orphaned questions could not be assigned (no Default stream found for their channel)", stillOrphaned);
        }

        return updated;
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
