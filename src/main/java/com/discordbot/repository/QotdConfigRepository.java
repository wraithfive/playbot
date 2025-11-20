package com.discordbot.repository;

import com.discordbot.entity.QotdConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DEPRECATED: Repository for legacy QotdConfig entity.
 * Kept for backward compatibility during migration to qotd_streams.
 */
@Deprecated
@Repository
public interface QotdConfigRepository extends JpaRepository<QotdConfig, QotdConfig.QotdConfigId> {
    List<QotdConfig> findByGuildId(String guildId);
}
