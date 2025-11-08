package com.discordbot.battle.repository;

import com.discordbot.battle.entity.PlayerCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing player character data in the battle system.
 * Provides database access for character CRUD operations.
 */
@Repository
public interface PlayerCharacterRepository extends JpaRepository<PlayerCharacter, Long> {

    /**
     * Find a character for a specific user in a specific guild.
     * Each user can have one character per guild.
     *
     * @param userId Discord user ID (snowflake)
     * @param guildId Discord guild ID (snowflake)
     * @return Optional containing the character if it exists
     */
    Optional<PlayerCharacter> findByUserIdAndGuildId(String userId, String guildId);

    /**
     * Find all characters in a specific guild.
     * Useful for guild leaderboards and statistics.
     *
     * @param guildId Discord guild ID (snowflake)
     * @return List of all characters in the guild
     */
    List<PlayerCharacter> findByGuildId(String guildId);

    /**
     * Delete a character for a specific user in a specific guild.
     *
     * @param userId Discord user ID (snowflake)
     * @param guildId Discord guild ID (snowflake)
     */
    void deleteByUserIdAndGuildId(String userId, String guildId);
}
