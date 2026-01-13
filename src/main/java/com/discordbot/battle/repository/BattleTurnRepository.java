package com.discordbot.battle.repository;

import com.discordbot.battle.entity.BattleTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for battle turn log persistence.
 * Provides queries for fetching battle history and analytics.
 */
@Repository
public interface BattleTurnRepository extends JpaRepository<BattleTurn, Long> {

    /**
     * Fetch all turns for a specific battle, ordered by turn number.
     */
    List<BattleTurn> findByBattleIdOrderByTurnNumberAsc(String battleId);

    /**
     * Fetch recent turns for a battle (useful for displaying recent combat log).
     */
    List<BattleTurn> findTop10ByBattleIdOrderByTurnNumberDesc(String battleId);

    /**
     * Count total turns in a battle.
     */
    long countByBattleId(String battleId);

    /**
     * Find all turns by a specific actor in a battle.
     */
    List<BattleTurn> findByBattleIdAndActorUserId(String battleId, String actorUserId);
}
