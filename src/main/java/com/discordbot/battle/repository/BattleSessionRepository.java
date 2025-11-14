package com.discordbot.battle.repository;

import com.discordbot.battle.entity.BattleSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for persistent battle sessions (Phase 7: Recovery).
 */
@Repository
public interface BattleSessionRepository extends JpaRepository<BattleSession, String> {

    /**
     * Find all battles with a specific status
     */
    List<BattleSession> findByStatus(BattleSession.BattleStatus status);

    /**
     * Find all active or pending battles (for recovery on startup)
     */
    @Query("SELECT b FROM BattleSession b WHERE b.status IN ('ACTIVE', 'PENDING')")
    List<BattleSession> findActiveBattles();

    /**
     * Find stale battles that haven't had action within threshold
     * (for automatic cleanup on startup or scheduled tasks)
     */
    @Query("SELECT b FROM BattleSession b WHERE b.status = 'ACTIVE' AND b.lastActionAt < :threshold")
    List<BattleSession> findStaleBattles(@Param("threshold") LocalDateTime threshold);

    /**
     * Find all battles for a specific guild
     */
    List<BattleSession> findByGuildId(String guildId);

    /**
     * Find all battles involving a specific user (as challenger or opponent)
     */
    @Query("SELECT b FROM BattleSession b WHERE b.challengerId = :userId OR b.opponentId = :userId")
    List<BattleSession> findByParticipant(@Param("userId") String userId);

    /**
     * Delete all battles for a guild (cleanup/admin operation)
     */
    void deleteByGuildId(String guildId);
}
