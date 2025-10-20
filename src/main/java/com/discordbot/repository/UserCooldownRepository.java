package com.discordbot.repository;

import com.discordbot.entity.UserCooldown;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing user cooldown data.
 * Provides database access for tracking when users last rolled in each guild.
 */
@Repository
public interface UserCooldownRepository extends JpaRepository<UserCooldown, Long> {

    /**
     * Find a cooldown record for a specific user in a specific guild
     *
     * @param userId Discord user ID (snowflake)
     * @param guildId Discord guild ID (snowflake)
     * @return Optional containing the cooldown record if it exists
     */
    Optional<UserCooldown> findByUserIdAndGuildId(String userId, String guildId);

    /**
     * Delete all cooldown records for a specific guild
     * Useful when bot is removed from a server
     *
     * @param guildId Discord guild ID (snowflake)
     */
    void deleteByGuildId(String guildId);
}
