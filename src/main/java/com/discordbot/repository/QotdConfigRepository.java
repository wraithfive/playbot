package com.discordbot.repository;

import com.discordbot.entity.QotdConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@SuppressWarnings("deprecation")
@Repository
public interface QotdConfigRepository extends JpaRepository<QotdConfig, QotdConfig.QotdConfigId> {
    List<QotdConfig> findByGuildId(String guildId);
}
