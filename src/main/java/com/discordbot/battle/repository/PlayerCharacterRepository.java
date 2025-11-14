package com.discordbot.battle.repository;

import com.discordbot.battle.entity.PlayerCharacter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // ============ Phase 6: Leaderboard Queries ============

    /**
     * Get top characters by ELO rating for a guild.
     * Phase 6: Leaderboards
     *
     * @param guildId Discord guild ID
     * @param pageable Pagination settings (limit)
     * @return List of characters ordered by ELO descending
     */
    @Query("SELECT c FROM PlayerCharacter c WHERE c.guildId = :guildId ORDER BY c.elo DESC, c.wins DESC")
    List<PlayerCharacter> findTopByElo(@Param("guildId") String guildId, Pageable pageable);

    /**
     * Get top characters by wins for a guild.
     * Phase 6: Leaderboards
     *
     * @param guildId Discord guild ID
     * @param pageable Pagination settings (limit)
     * @return List of characters ordered by wins descending
     */
    @Query("SELECT c FROM PlayerCharacter c WHERE c.guildId = :guildId ORDER BY c.wins DESC, c.elo DESC")
    List<PlayerCharacter> findTopByWins(@Param("guildId") String guildId, Pageable pageable);

    /**
     * Get top characters by level for a guild.
     * Phase 6: Leaderboards
     *
     * @param guildId Discord guild ID
     * @param pageable Pagination settings (limit)
     * @return List of characters ordered by level descending, then XP
     */
    @Query("SELECT c FROM PlayerCharacter c WHERE c.guildId = :guildId ORDER BY c.level DESC, c.xp DESC")
    List<PlayerCharacter> findTopByLevel(@Param("guildId") String guildId, Pageable pageable);

    /**
     * Get characters with most battles (for activity leaderboard).
     * Phase 6: Leaderboards
     *
     * @param guildId Discord guild ID
     * @param pageable Pagination settings (limit)
     * @return List of characters ordered by total battles descending
     */
    @Query("SELECT c FROM PlayerCharacter c WHERE c.guildId = :guildId ORDER BY (c.wins + c.losses + c.draws) DESC")
    List<PlayerCharacter> findTopByActivity(@Param("guildId") String guildId, Pageable pageable);
}
