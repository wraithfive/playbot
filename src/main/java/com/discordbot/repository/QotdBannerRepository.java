package com.discordbot.repository;

import com.discordbot.entity.QotdBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * DEPRECATED: Repository for legacy QotdBanner entity.
 * Kept for backward compatibility during migration to qotd_streams.
 */
@Deprecated
public interface QotdBannerRepository extends JpaRepository<QotdBanner, Long> {
    Optional<QotdBanner> findByChannelId(String channelId);
    void deleteByChannelId(String channelId);
}
