package com.discordbot.battle.repository;

import com.discordbot.battle.entity.BattleStatusEffect;
import com.discordbot.battle.entity.StatusEffectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing battle status effects.
 * Provides methods to query, create, update, and delete status effects.
 */
@Repository
public interface BattleStatusEffectRepository extends JpaRepository<BattleStatusEffect, Long> {

    /**
     * Find all active status effects for a specific battle.
     */
    List<BattleStatusEffect> findByBattleId(String battleId);

    /**
     * Find all active status effects for a specific user in a battle.
     */
    List<BattleStatusEffect> findByBattleIdAndAffectedUserId(String battleId, String affectedUserId);

    /**
     * Find a specific status effect by battle, user, and effect type.
     * Used to check if a specific effect is already active before applying.
     */
    Optional<BattleStatusEffect> findByBattleIdAndAffectedUserIdAndEffectType(
        String battleId, String affectedUserId, StatusEffectType effectType);

    /**
     * Find all effects of a specific type in a battle.
     */
    List<BattleStatusEffect> findByBattleIdAndEffectType(String battleId, StatusEffectType effectType);

    /**
     * Delete all status effects for a specific battle (cleanup when battle ends).
     */
    @Transactional
    void deleteByBattleId(String battleId);

    /**
     * Delete all status effects for a specific user in a battle.
     */
    @Transactional
    void deleteByBattleIdAndAffectedUserId(String battleId, String affectedUserId);

    /**
     * Count active effects on a specific user in a battle.
     */
    long countByBattleIdAndAffectedUserId(String battleId, String affectedUserId);
}
