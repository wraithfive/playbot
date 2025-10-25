package com.discordbot.repository;

import com.discordbot.entity.QotdBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface QotdBannerRepository extends JpaRepository<QotdBanner, Long> {
    Optional<QotdBanner> findByChannelId(String channelId);
    void deleteByChannelId(String channelId);
}
