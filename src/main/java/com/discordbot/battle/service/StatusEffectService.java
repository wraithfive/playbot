package com.discordbot.battle.service;

import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.entity.BattleStatusEffect;
import com.discordbot.battle.entity.StatusEffectType;
import com.discordbot.battle.repository.BattleStatusEffectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing status effects during battles.
 * Handles application, stacking, ticking, and expiration of buffs/debuffs.
 */
@Service
public class StatusEffectService {

    private static final Logger logger = LoggerFactory.getLogger(StatusEffectService.class);

    private final BattleStatusEffectRepository repository;

    public StatusEffectService(BattleStatusEffectRepository repository) {
        this.repository = repository;
    }

    /**
     * Apply a status effect to a character in a battle.
     * If the effect is stackable and already exists, adds stacks.
     * If the effect is non-stackable and already exists, refreshes duration.
     *
     * <p><b>Thread Safety (Phase 9):</b> Protected against race conditions via unique
     * constraint on (battle_id, affected_user_id, effect_type). If concurrent applications
     * occur, the database constraint prevents duplicates and this method retries once.</p>
     *
     * @param battleId ID of the battle
     * @param affectedUserId User ID of the affected character
     * @param effectType Type of status effect
     * @param durationTurns Duration in turns
     * @param stacks Number of stacks to apply
     * @param magnitude Power/magnitude of the effect
     * @param sourceUserId User ID of the source (who applied it)
     * @param sourceAbilityKey Ability key that applied this effect
     * @param currentTurn Current turn number when applied
     * @return The applied or updated status effect
     * @throws org.springframework.dao.DataIntegrityViolationException if retry fails
     */
    @Transactional
    public BattleStatusEffect applyEffect(String battleId, String affectedUserId,
                                         StatusEffectType effectType, int durationTurns,
                                         int stacks, int magnitude,
                                         String sourceUserId, String sourceAbilityKey,
                                         int currentTurn) {
        try {
            // Check if effect already exists
            Optional<BattleStatusEffect> existing = repository.findByBattleIdAndAffectedUserIdAndEffectType(
                battleId, affectedUserId, effectType);

            if (existing.isPresent()) {
                BattleStatusEffect effect = existing.get();

                if (effectType.isStackable()) {
                    // Stackable effect: add stacks and refresh duration
                    effect.addStacks(stacks);
                    effect.refreshDuration(durationTurns);
                    logger.debug("Stacked {} effect on user {} in battle {}: {} stacks, {} turns",
                        effectType, affectedUserId, battleId, effect.getStacks(), effect.getDurationTurns());
                } else {
                    // Non-stackable effect: just refresh duration
                    effect.refreshDuration(durationTurns);
                    logger.debug("Refreshed {} effect on user {} in battle {}: {} turns",
                        effectType, affectedUserId, battleId, effect.getDurationTurns());
                }

                return repository.save(effect);
            } else {
                // New effect
                BattleStatusEffect effect = new BattleStatusEffect(
                    battleId, affectedUserId, effectType, durationTurns,
                    stacks, magnitude, sourceUserId, sourceAbilityKey, currentTurn);

                logger.debug("Applied new {} effect to user {} in battle {}: {} stacks, {} turns, magnitude {}",
                    effectType, affectedUserId, battleId, stacks, durationTurns, magnitude);

                return repository.save(effect);
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Phase 9: Race condition protection (migration 028 unique constraint)
            // Another thread created the effect between our check and save. Retry once.
            logger.debug("Race condition detected applying {} to user {} in battle {}, retrying...",
                effectType, affectedUserId, battleId);

            Optional<BattleStatusEffect> existing = repository.findByBattleIdAndAffectedUserIdAndEffectType(
                battleId, affectedUserId, effectType);

            if (existing.isPresent()) {
                BattleStatusEffect effect = existing.get();
                if (effectType.isStackable()) {
                    effect.addStacks(stacks);
                    effect.refreshDuration(durationTurns);
                } else {
                    effect.refreshDuration(durationTurns);
                }
                return repository.save(effect);
            } else {
                logger.error("Failed to apply status effect after race condition retry", e);
                throw e;
            }
        }
    }

    /**
     * Process status effects at the start of a character's turn.
     * Handles effects like BURN, POISON, REGEN that trigger at turn start.
     *
     * @param battle The active battle
     * @param userId User ID whose turn is starting
     * @return Result object containing damage/healing and messages
     */
    @Transactional
    public TurnStartEffectResult processTurnStartEffects(ActiveBattle battle, String userId) {
        List<BattleStatusEffect> effects = getActiveEffects(battle.getId(), userId);

        int totalDamage = 0;
        int totalHealing = 0;
        StringBuilder messages = new StringBuilder();

        for (BattleStatusEffect effect : effects) {
            switch (effect.getEffectType()) {
                case BURN, POISON, BLEED -> {
                    int damage = effect.getTotalMagnitude();
                    totalDamage += damage;
                    messages.append(String.format("💥 %s takes %d damage from %s!\n",
                        mention(userId), damage, effect.getEffectType().getDisplayName()));
                }
                case REGEN -> {
                    int healing = effect.getTotalMagnitude();
                    totalHealing += healing;
                    messages.append(String.format("💚 %s heals %d HP from %s!\n",
                        mention(userId), healing, effect.getEffectType().getDisplayName()));
                }
                case STUN -> {
                    messages.append(String.format("😵 %s is stunned and cannot act!\n",
                        mention(userId)));
                }
                default -> {
                    // Other effects (SHIELD, PROTECTION, STRENGTH, VULNERABILITY, WEAKNESS, SLOW, HASTE) don't trigger at turn start
                }
            }
        }

        // Apply damage/healing to battle
        if (totalDamage > 0) {
            applyDamageToBattle(battle, userId, totalDamage);
        }
        if (totalHealing > 0) {
            applyHealingToBattle(battle, userId, totalHealing);
        }

        return new TurnStartEffectResult(totalDamage, totalHealing, messages.toString(), hasStun(effects));
    }

    /**
     * Tick all status effects for a character, decrementing durations and removing expired effects.
     * Called at the end of a character's turn.
     *
     * @param battleId ID of the battle
     * @param userId User ID whose effects to tick
     * @return Number of effects that expired
     */
    @Transactional
    public int tickEffects(String battleId, String userId) {
        List<BattleStatusEffect> effects = getActiveEffects(battleId, userId);
        int expiredCount = 0;

        for (BattleStatusEffect effect : effects) {
            if (effect.tick()) {
                // Effect expired
                repository.delete(effect);
                expiredCount++;
                logger.debug("Expired {} effect on user {} in battle {}",
                    effect.getEffectType(), userId, battleId);
            } else {
                // Still active, save updated duration
                repository.save(effect);
            }
        }

        return expiredCount;
    }

    /**
     * Remove a specific status effect from a character.
     *
     * @param battleId ID of the battle
     * @param userId User ID
     * @param effectType Type of effect to remove
     * @return true if effect was removed, false if not found
     */
    @Transactional
    public boolean removeEffect(String battleId, String userId, StatusEffectType effectType) {
        Optional<BattleStatusEffect> effect = repository.findByBattleIdAndAffectedUserIdAndEffectType(
            battleId, userId, effectType);

        if (effect.isPresent()) {
            repository.delete(effect.get());
            logger.debug("Removed {} effect from user {} in battle {}", effectType, userId, battleId);
            return true;
        }

        return false;
    }

    /**
     * Remove all status effects from a character.
     *
     * @param battleId ID of the battle
     * @param userId User ID
     */
    @Transactional
    public void removeAllEffects(String battleId, String userId) {
        repository.deleteByBattleIdAndAffectedUserId(battleId, userId);
        logger.debug("Removed all effects from user {} in battle {}", userId, battleId);
    }

    /**
     * Clean up all status effects for a battle (when battle ends).
     *
     * @param battleId ID of the battle
     */
    @Transactional
    public void cleanupBattleEffects(String battleId) {
        repository.deleteByBattleId(battleId);
        logger.debug("Cleaned up all effects for battle {}", battleId);
    }

    /**
     * Get all active status effects for a character in a battle.
     */
    public List<BattleStatusEffect> getActiveEffects(String battleId, String userId) {
        return repository.findByBattleIdAndAffectedUserId(battleId, userId).stream()
            .filter(effect -> !effect.isExpired())
            .collect(Collectors.toList());
    }

    /**
     * Check if a character has a specific status effect active.
     */
    public boolean hasEffect(String battleId, String userId, StatusEffectType effectType) {
        return repository.findByBattleIdAndAffectedUserIdAndEffectType(battleId, userId, effectType)
            .filter(effect -> !effect.isExpired())
            .isPresent();
    }

    /**
     * Get total AC modifier from active status effects (HASTE, SLOW, etc.)
     */
    public int getAcModifier(String battleId, String userId) {
        List<BattleStatusEffect> effects = getActiveEffects(battleId, userId);
        int modifier = 0;

        for (BattleStatusEffect effect : effects) {
            switch (effect.getEffectType()) {
                case HASTE -> modifier += effect.getMagnitude();  // Positive AC bonus
                case SLOW -> modifier -= effect.getMagnitude();    // Negative AC penalty
                default -> {} // Other effects don't modify AC
            }
        }

        return modifier;
    }

    /**
     * Get total attack roll modifier from active status effects.
     */
    public int getAttackModifier(String battleId, String userId) {
        List<BattleStatusEffect> effects = getActiveEffects(battleId, userId);
        int modifier = 0;

        for (BattleStatusEffect effect : effects) {
            switch (effect.getEffectType()) {
                case HASTE -> modifier += effect.getMagnitude();  // Attack bonus
                case SLOW -> modifier -= effect.getMagnitude();    // Attack penalty
                default -> {} // Other effects don't modify attack
            }
        }

        return modifier;
    }

    /**
     * Get total damage modifier from active status effects (percentage).
     * Returns value like 110 for +10%, 80 for -20%.
     */
    public int getDamageModifierPercent(String battleId, String userId) {
        List<BattleStatusEffect> effects = getActiveEffects(battleId, userId);
        int percent = 100;  // Base 100%

        for (BattleStatusEffect effect : effects) {
            switch (effect.getEffectType()) {
                case STRENGTH -> percent += effect.getMagnitude();      // Increase damage
                case WEAKNESS -> percent -= effect.getMagnitude();      // Decrease damage
                default -> {} // Other effects don't modify outgoing damage
            }
        }

        return Math.max(0, percent);  // Can't go negative
    }

    /**
     * Get total incoming damage modifier from active status effects (percentage).
     */
    public int getIncomingDamageModifierPercent(String battleId, String userId) {
        List<BattleStatusEffect> effects = getActiveEffects(battleId, userId);
        int percent = 100;  // Base 100%

        for (BattleStatusEffect effect : effects) {
            switch (effect.getEffectType()) {
                case PROTECTION -> percent -= effect.getMagnitude();      // Reduce damage taken
                case VULNERABILITY -> percent += effect.getMagnitude();   // Increase damage taken
                default -> {} // Other effects don't modify incoming damage
            }
        }

        return Math.max(0, percent);  // Can't go negative
    }

    /**
     * Get shield HP value if character has active shield effect.
     * Returns 0 if no shield active.
     */
    public int getShieldValue(String battleId, String userId) {
        return repository.findByBattleIdAndAffectedUserIdAndEffectType(battleId, userId, StatusEffectType.SHIELD)
            .filter(effect -> !effect.isExpired())
            .map(BattleStatusEffect::getTotalMagnitude)
            .orElse(0);
    }

    /**
     * Consume shield HP when character takes damage.
     * Returns remaining damage after shield absorption.
     */
    @Transactional
    public int consumeShield(String battleId, String userId, int incomingDamage) {
        Optional<BattleStatusEffect> shieldOpt = repository.findByBattleIdAndAffectedUserIdAndEffectType(
            battleId, userId, StatusEffectType.SHIELD);

        if (shieldOpt.isEmpty() || incomingDamage <= 0) {
            return incomingDamage;
        }

        BattleStatusEffect shield = shieldOpt.get();
        int shieldHp = shield.getTotalMagnitude();

        if (shieldHp >= incomingDamage) {
            // Shield absorbs all damage
            shield.setMagnitude(shield.getMagnitude() - (incomingDamage / Math.max(1, shield.getStacks())));
            if (shield.getMagnitude() <= 0) {
                repository.delete(shield);  // Shield depleted
            } else {
                repository.save(shield);
            }
            return 0;
        } else {
            // Shield breaks, some damage gets through
            repository.delete(shield);
            return incomingDamage - shieldHp;
        }
    }

    /**
     * Get formatted display string of all active effects for a character.
     */
    public String getEffectsDisplayString(String battleId, String userId) {
        List<BattleStatusEffect> effects = getActiveEffects(battleId, userId);

        if (effects.isEmpty()) {
            return "";
        }

        return effects.stream()
            .map(BattleStatusEffect::getDisplayString)
            .collect(Collectors.joining(", "));
    }

    // --- Helper Methods ---

    private boolean hasStun(List<BattleStatusEffect> effects) {
        return effects.stream()
            .anyMatch(e -> e.getEffectType() == StatusEffectType.STUN && !e.isExpired());
    }

    private void applyDamageToBattle(ActiveBattle battle, String userId, int damage) {
        if (userId.equals(battle.getChallengerId())) {
            battle.setChallengerHp(Math.max(0, battle.getChallengerHp() - damage));
        } else {
            battle.setOpponentHp(Math.max(0, battle.getOpponentHp() - damage));
        }
    }

    private void applyHealingToBattle(ActiveBattle battle, String userId, int healing) {
        if (userId.equals(battle.getChallengerId())) {
            int maxHp = battle.getChallengerMaxHp();
            battle.setChallengerHp(Math.min(maxHp, battle.getChallengerHp() + healing));
        } else {
            int maxHp = battle.getOpponentMaxHp();
            battle.setOpponentHp(Math.min(maxHp, battle.getOpponentHp() + healing));
        }
    }

    private String mention(String userId) {
        return "<@" + userId + ">";
    }

    /**
     * Result object for turn start effect processing.
     */
    public record TurnStartEffectResult(
        int damageDealt,
        int healingDone,
        String messages,
        boolean hasStun
    ) {}
}
