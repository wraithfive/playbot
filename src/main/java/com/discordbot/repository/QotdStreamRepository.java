package com.discordbot.repository;

import com.discordbot.entity.QotdStream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for QOTD stream entities.
 */
@Repository
public interface QotdStreamRepository extends JpaRepository<QotdStream, Long> {

    /**
     * Find all streams in a specific channel, ordered by ID (creation order).
     */
    List<QotdStream> findByGuildIdAndChannelIdOrderByIdAsc(String guildId, String channelId);

    /**
     * Find all streams in a guild, ordered by channel and creation order.
     */
    List<QotdStream> findByGuildIdOrderByChannelIdAscIdAsc(String guildId);

    /**
     * Find all enabled streams (for scheduler).
     */
    List<QotdStream> findByEnabledTrue();

    /**
     * Check if any stream exists for a specific channel.
     * Used by migration service to avoid duplicate default streams.
     */
    boolean existsByGuildIdAndChannelId(String guildId, String channelId);

    /**
     * Find stream by guild, channel, and name.
     * Used for uniqueness validation when creating/updating streams.
     */
    Optional<QotdStream> findByGuildIdAndChannelIdAndStreamName(
        String guildId, String channelId, String streamName);

    /**
     * Count streams in a channel (for 5-stream limit enforcement).
     */
    long countByGuildIdAndChannelId(String guildId, String channelId);

    /**
     * Find the first stream in a channel (default stream after migration).
     */
    Optional<QotdStream> findFirstByGuildIdAndChannelIdOrderByIdAsc(String guildId, String channelId);
}
