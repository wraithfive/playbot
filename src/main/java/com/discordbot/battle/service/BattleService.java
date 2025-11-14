package com.discordbot.battle.service;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.effect.CharacterStatsCalculator;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.entity.BattleTurn;
import com.discordbot.battle.entity.CharacterAbility;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.BattleTurnRepository;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.util.CharacterDerivedStats;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core service managing battle lifecycle and combat resolution.
 * MVP: In-memory transient battles (no persistence) with simple melee attacks.
 */
@Service
public class BattleService {

    private static final Logger logger = LoggerFactory.getLogger(BattleService.class);
    
    // Constants for combat calculations
    private static final int ABILITY_SCORE_BASE = 10;
    private static final int ABILITY_MODIFIER_DIVISOR = 2;

    private final PlayerCharacterRepository characterRepository;
    private final CharacterAbilityRepository characterAbilityRepository;
    private final BattleTurnRepository battleTurnRepository;
    private final BattleProperties battleProperties;

    // Cache of active/pending battles keyed by battleId with adaptive expiry.
    // Active / pending battles: up to 60 minutes since last access.
    // Ended battles: trimmed to a short retention (5 minutes) for UI refresh / logs.
    private final Cache<String, ActiveBattle> battles = Caffeine.newBuilder()
        .expireAfter(new Expiry<String, ActiveBattle>() {
            private final long ACTIVE_NANOS = TimeUnit.MINUTES.toNanos(60);
            private final long ENDED_NANOS = TimeUnit.MINUTES.toNanos(5);
            @Override
            public long expireAfterCreate(String key, ActiveBattle value, long currentTime) {
                return value.isEnded() ? ENDED_NANOS : ACTIVE_NANOS;
            }
            @Override
            public long expireAfterUpdate(String key, ActiveBattle value, long currentTime, long currentDuration) {
                // If it transitioned to ENDED shorten retention window.
                if (value.isEnded() && currentDuration > ENDED_NANOS) {
                    return ENDED_NANOS;
                }
                return value.isEnded() ? Math.min(currentDuration, ENDED_NANOS) : ACTIVE_NANOS;
            }
            @Override
            public long expireAfterRead(String key, ActiveBattle value, long currentTime, long currentDuration) {
                // Reading does not extend ended battles beyond ENDED_NANOS
                return value.isEnded() ? Math.min(currentDuration, ENDED_NANOS) : ACTIVE_NANOS;
            }
        })
        .maximumSize(1000)
        .build();

    // Cooldown cache: tracks when users last completed a battle (userId -> epoch millis).
    // Expires entries based on configured cooldown duration.
    private final Cache<String, Long> battleCooldowns = Caffeine.newBuilder()
        .expireAfterWrite(TimeUnit.HOURS.toNanos(24), TimeUnit.NANOSECONDS) // Max cache duration
        .maximumSize(10000)
        .build();

    // Per-user lightweight monitor objects to prevent race conditions when issuing challenges.
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    public BattleService(PlayerCharacterRepository characterRepository,
                         CharacterAbilityRepository characterAbilityRepository,
                         BattleTurnRepository battleTurnRepository,
                         BattleProperties battleProperties) {
        this.characterRepository = characterRepository;
        this.characterAbilityRepository = characterAbilityRepository;
        this.battleTurnRepository = battleTurnRepository;
        this.battleProperties = battleProperties;
    }

    /**
     * Issue a duel challenge (creates a PENDING battle) with race-condition prevention.
     * <p>Concurrency: Two simultaneous requests involving the same users could previously both pass
     * busy/duplicate checks and create duplicate battles. We now acquire ordered per-user locks
     * to serialize challenge creation for the pair.</p>
     * @throws IllegalStateException if validation fails (duplicate, busy, cap exceeded, missing character)
     */
    public ActiveBattle createChallenge(String guildId, String challengerUserId, String opponentUserId) {
        // Order locks deterministically to avoid deadlock
        String[] ordered = {challengerUserId, opponentUserId};
        java.util.Arrays.sort(ordered, Comparator.naturalOrder());
        Object lockA = userLocks.computeIfAbsent(ordered[0], k -> new Object());
        Object lockB = userLocks.computeIfAbsent(ordered[1], k -> new Object());
        synchronized (lockA) {
            synchronized (lockB) {
                // Clean up any expired pending challenges before enforcing constraints (without lock cleanup)
                cleanUpExpiredChallengesOnly();
                // NEW: Only challenger must already have a character; opponent may create one later before accepting.
                ensureChallengerHasCharacter(guildId, challengerUserId);
                if (findExistingBetween(challengerUserId, opponentUserId).isPresent()) {
                    throw new IllegalStateException("A battle/challenge already exists between these users.");
                }
                if (isUserBusy(challengerUserId)) {
                    throw new IllegalStateException("You already have an active or pending battle.");
                }
                if (isUserBusy(opponentUserId)) {
                    throw new IllegalStateException("That opponent is already in an active or pending battle.");
                }
                int maxPerGuild = battleProperties.getCombat().getMaxConcurrentPerGuild();
                long currentInGuild = battles.asMap().values().stream()
                    .filter(b -> !b.isEnded() && Objects.equals(guildId, b.getGuildId()))
                    .count();
                if (currentInGuild >= maxPerGuild) {
                    throw new IllegalStateException("This server has reached the maximum number of concurrent battles. Try again later.");
                }
                ActiveBattle battle = ActiveBattle.createPending(guildId, challengerUserId, opponentUserId);
                battles.put(battle.getId(), battle);
                logger.info("Challenge created battleId={} guild={} challenger={} opponent={}", battle.getId(), guildId, challengerUserId, opponentUserId);
                return battle;
            }
        }
    }

    /** Accept a pending challenge and start battle. */
    public ActiveBattle acceptChallenge(String battleId, String acceptingUserId) {
        ActiveBattle battle = getBattleOrThrow(battleId);
        // Expire stale pending challenges on accept
        if (battle.isPending() && isExpired(battle)) {
            expireChallenge(battle);
            throw new IllegalStateException("This challenge has expired.");
        }
        if (!battle.isPending()) {
            throw new IllegalStateException("Battle not in pending state");
        }
        if (!Objects.equals(acceptingUserId, battle.getOpponentId())) {
            throw new IllegalStateException("Only opponent may accept the challenge");
        }
        // Validate characters still exist. Challenger must still have one; opponent must have created one now.
        if (!hasCharacter(battle.getGuildId(), battle.getChallengerId())) {
            throw new IllegalStateException("The challenger no longer has a character. They must recreate it before the battle can start.");
        }
        if (!hasCharacter(battle.getGuildId(), battle.getOpponentId())) {
            throw new IllegalStateException("You need to create a character first with /create-character before accepting.");
        }
        int challengerHp = computeStartingHp(battle.getGuildId(), battle.getChallengerId());
        int opponentHp = computeStartingHp(battle.getGuildId(), battle.getOpponentId());
        battle.start(challengerHp, opponentHp);
        logger.info("Battle started battleId={} CHP={} OHP={}", battle.getId(), challengerHp, opponentHp);
        return battle;
    }

    /** Decline a pending challenge. */
    public ActiveBattle declineChallenge(String battleId, String decliningUserId) {
        ActiveBattle battle = getBattleOrThrow(battleId);
        battle.decline(decliningUserId);
        logger.info("Battle declined battleId={} by {}", battle.getId(), decliningUserId);
        return battle;
    }

    /** Perform an attack action by current turn user. */
    public AttackResult performAttack(String battleId, String attackerUserId) {
        ActiveBattle battle = getBattleOrThrow(battleId);
        if (!battle.isActive()) {
            throw new IllegalStateException("Battle not active");
        }
        if (!Objects.equals(attackerUserId, battle.getCurrentTurnUserId())) {
            throw new IllegalStateException("Not your turn");
        }

        boolean attackerIsChallenger = attackerUserId.equals(battle.getChallengerId());
        String defenderUserId = attackerIsChallenger ? battle.getOpponentId() : battle.getChallengerId();

        PlayerCharacter attackerChar = getCharacter(battle.getGuildId(), attackerUserId);
        PlayerCharacter defenderChar = getCharacter(battle.getGuildId(), defenderUserId);

        // Get learned abilities for both characters
        List<CharacterAbility> attackerAbilities = characterAbilityRepository.findByCharacter(attackerChar);
        List<CharacterAbility> defenderAbilities = characterAbilityRepository.findByCharacter(defenderChar);

        // Calculate base HP for stats calculation
        int attackerBaseHp = getBaseHpForClass(attackerChar.getCharacterClass());
        int defenderBaseHp = getBaseHpForClass(defenderChar.getCharacterClass());

        // Calculate combat stats with ability bonuses
        CharacterStatsCalculator.CombatStats attackerStats =
            CharacterStatsCalculator.calculateStats(attackerChar, attackerAbilities, attackerBaseHp);
        CharacterStatsCalculator.CombatStats defenderStats =
            CharacterStatsCalculator.calculateStats(defenderChar, defenderAbilities, defenderBaseHp);

        int attackRoll = battle.rollD20();
        int strToHitMod = abilityMod(attackerChar.getStrength());
        int totalAttack = attackRoll + strToHitMod;

        // Apply defender's base AC plus any temporary AC bonus from defend
        int baseArmorClass = defenderStats.armorClass();
        int tempAcBonus = battle.getEffectiveAcBonus(defenderUserId);
        int armorClass = baseArmorClass + tempAcBonus;

        boolean crit = attackRoll >= battleProperties.getCombat().getCrit().getThreshold();
        boolean hit = crit || totalAttack >= armorClass;

        int damage = 0;
        if (hit) {
            int baseWeapon = battle.rollD6();
            // attackDamageBonus already includes STR mod, so just add it once
            damage = baseWeapon + attackerStats.attackDamageBonus();
            if (crit) {
                double critMultiplier = battleProperties.getCombat().getCrit().getMultiplier();
                // Apply crit damage bonus (percentage increase)
                if (attackerStats.critDamageBonus() > 0) {
                    critMultiplier += (attackerStats.critDamageBonus() / 100.0);
                }
                damage = (int) Math.round(damage * critMultiplier);
            }
            damage = Math.max(0, damage); // Prevent negative damage if STR mod < 0
            if (attackerIsChallenger) {
                battle.applyDamageToOpponent(damage);
            } else {
                battle.applyDamageToChallenger(damage);
            }
        }

        StringBuilder logLine = new StringBuilder()
            .append("{USER:").append(attackerUserId).append("}").append(" attacks (roll=").append(attackRoll)
            .append(", strToHitMod=").append(strToHitMod)
            .append(", total=").append(totalAttack)
            .append(", AC=").append(armorClass);
        if (tempAcBonus > 0) {
            logLine.append(" [+").append(tempAcBonus).append(" defend]");
        }
        logLine.append(") ");
        if (!hit) {
            logLine.append("and misses");
        } else if (crit) {
            logLine.append("CRITS for ").append(damage).append(" dmg");
        } else {
            logLine.append("hits for ").append(damage).append(" dmg");
        }
        battle.addLog(logLine.toString());

        // Reset last action timestamp
        battle.resetLastActionAt();

        // Persist turn to database
        persistBattleTurn(battle, attackerUserId, defenderUserId, BattleTurn.ActionType.ATTACK,
                         null, attackRoll, totalAttack, armorClass, damage, hit, crit);

        // Check end condition
        boolean ended = battle.getOpponentHp() <= 0 || battle.getChallengerHp() <= 0;
        String winner = null;
        if (ended) {
            winner = battle.getOpponentHp() <= 0 ? battle.getChallengerId() : battle.getOpponentId();
            battle.end(winner);
            recordBattleCompletion(battle.getChallengerId());
            recordBattleCompletion(battle.getOpponentId());
            logger.info("Battle ended battleId={} winner={} CHP={} OHP={}", battle.getId(), winner, battle.getChallengerHp(), battle.getOpponentHp());
        } else {
            battle.advanceTurn();
        }

        return new AttackResult(battle, attackRoll, totalAttack, armorClass, damage, hit, crit, winner);
    }

    /** Container for attack outcome. */
    public record AttackResult(ActiveBattle battle,
                               int rawRoll,
                               int totalAttack,
                               int defenderAc,
                               int damage,
                               boolean hit,
                               boolean crit,
                               String winnerUserId) {}

    /** 
     * Compute starting HP using centralized D&D 5e formula from CharacterDerivedStats.
     * Level 1: hitDieMax + CON modifier
     */
    private int computeStartingHp(String guildId, String userId) {
        PlayerCharacter pc = getCharacter(guildId, userId);
        return CharacterDerivedStats.computeHp(pc, battleProperties);
    }

    /** 
     * Get base HP for a character class (D&D 5e hit die maximum).
     * Used for stats calculator; delegates to CharacterDerivedStats for consistency.
     */
    private int getBaseHpForClass(String characterClass) {
        return CharacterDerivedStats.getBaseHpForClass(characterClass, battleProperties);
    }

    private int abilityMod(int score) { 
        return (score - ABILITY_SCORE_BASE) / ABILITY_MODIFIER_DIVISOR; 
    }

    private PlayerCharacter getCharacter(String guildId, String userId) {
        return characterRepository.findByUserIdAndGuildId(userId, guildId)
            .orElseThrow(() -> new IllegalStateException("Character not found for user=" + userId));
    }

    /** Ensure challenger has a character. */
    private void ensureChallengerHasCharacter(String guildId, String challengerUserId) {
        if (!hasCharacter(guildId, challengerUserId)) {
            throw new IllegalStateException("You must create a character first with /create-character");
        }
    }

    /** Public helper to check if a user has a character (used by interaction layer). */
    public boolean hasCharacter(String guildId, String userId) {
        return characterRepository.findByUserIdAndGuildId(userId, guildId).isPresent();
    }

    /**
     * Finds an existing (non-ended) battle between two users regardless of ordering.
     */
    public Optional<ActiveBattle> findExistingBetween(String userA, String userB) {
        Collection<ActiveBattle> all = battles.asMap().values();
        return all.stream().filter(b -> !b.isEnded() && (
            (b.getChallengerId().equals(userA) && b.getOpponentId().equals(userB)) ||
            (b.getChallengerId().equals(userB) && b.getOpponentId().equals(userA))
        )).findFirst();
    }

    /**
     * Returns the battle or throws if not in cache.
     */
    public ActiveBattle getBattleOrThrow(String battleId) {
        ActiveBattle battle = battles.getIfPresent(battleId);
        if (battle == null) throw new IllegalStateException("Battle not found: " + battleId);
        return battle;
    }

    /** Returns true if the user is in any non-ended battle (pending or active). */
    public boolean isUserBusy(String userId) {
        return battles.asMap().values().stream()
            .anyMatch(b -> !b.isEnded() && (Objects.equals(userId, b.getChallengerId()) || Objects.equals(userId, b.getOpponentId())));
    }

    /** Removes or ends challenges that have exceeded the pending expiration threshold. */
    public int cleanUpExpiredChallenges() {
        int count = cleanUpExpiredChallengesOnly();
        // Clean up userLocks for users no longer in any battles to prevent memory leak
        cleanupUnusedLocks();
        return count;
    }

    /** Removes or ends challenges that have exceeded the pending expiration threshold (without lock cleanup). */
    private int cleanUpExpiredChallengesOnly() {
        AtomicInteger count = new AtomicInteger();
        battles.asMap().values().stream()
            .filter(this::isExpired)
            .forEach(b -> {
                expireChallenge(b);
                count.incrementAndGet();
            });
        if (count.get() > 0) {
            logger.info("Expired {} stale battle challenge(s)", count.get());
        }
        return count.get();
    }

    /**
     * Removes lock objects for users not currently involved in any active/pending battles.
     * Prevents unbounded growth of the userLocks map.
     */
    private void cleanupUnusedLocks() {
        // Collect all user IDs currently in battles
        var activeUsers = battles.asMap().values().stream()
            .filter(b -> !b.isEnded())
            .flatMap(b -> java.util.stream.Stream.of(b.getChallengerId(), b.getOpponentId()))
            .collect(java.util.stream.Collectors.toSet());
        
        // Remove locks for users not in the active set
        userLocks.keySet().removeIf(userId -> !activeUsers.contains(userId));
    }

    private boolean isExpired(ActiveBattle b) {
        if (!b.isPending()) return false;
        long ageMs = System.currentTimeMillis() - b.getCreatedAt();
        long ttlMs = TimeUnit.SECONDS.toMillis(battleProperties.getChallenge().getExpireSeconds());
        return ageMs >= ttlMs; // Expire immediately when ttl==0
    }

    private void expireChallenge(ActiveBattle b) {
        b.end(null);
        b.addLog("Challenge expired due to inactivity");
        // Note: Caffeine cache will auto-expire ended battles per the ENDED_NANOS policy.
        // We keep them briefly for UI refresh, then they're automatically evicted.
    }

    // ========== Phase 3: New Battle Actions and Features ==========

    /**
     * Find a pending battle where the given userId is the opponent.
     * Used by accept/decline command handlers.
     */
    public Optional<ActiveBattle> findPendingBattleForOpponent(String userId) {
        return battles.asMap().values().stream()
            .filter(b -> b.isPending() && Objects.equals(userId, b.getOpponentId()))
            .findFirst();
    }

    /**
     * Find an active battle where the given userId is a participant (challenger or opponent).
     * Used by action command handlers to find the battle to act upon.
     */
    public Optional<ActiveBattle> findActiveBattleForUser(String userId) {
        return battles.asMap().values().stream()
            .filter(b -> b.isActive() && (Objects.equals(userId, b.getChallengerId()) || Objects.equals(userId, b.getOpponentId())))
            .findFirst();
    }

    /**
     * Forfeit the battle, making the other participant the winner.
     * Creates a FORFEIT turn log entry and updates battle status to ENDED.
     */
    public ActiveBattle forfeit(String battleId, String userId) {
        ActiveBattle battle = getBattleOrThrow(battleId);
        if (!battle.isActive()) {
            throw new IllegalStateException("Battle not active");
        }
        if (!Objects.equals(userId, battle.getChallengerId()) && !Objects.equals(userId, battle.getOpponentId())) {
            throw new IllegalStateException("You are not a participant in this battle");
        }

        String winner = Objects.equals(userId, battle.getChallengerId()) ? battle.getOpponentId() : battle.getChallengerId();
        battle.addLog("{USER:" + userId + "} forfeits the battle");
        battle.end(winner);

        // Persist forfeit turn
        persistBattleTurn(battle, userId, winner, BattleTurn.ActionType.FORFEIT,
                         null, null, null, null, 0, false, false);

        // Record completion for both participants
        recordBattleCompletion(battle.getChallengerId());
        recordBattleCompletion(battle.getOpponentId());

        logger.info("Battle forfeited battleId={} by {} winner={}", battle.getId(), userId, winner);
        return battle;
    }

    /**
     * Perform a defend action, granting temporary AC bonus (+2) for one turn.
     * Returns DefendResult with battle state, temp AC bonus, and winner if battle ended.
     */
    public DefendResult performDefend(String battleId, String userId) {
        ActiveBattle battle = getBattleOrThrow(battleId);
        if (!battle.isActive()) {
            throw new IllegalStateException("Battle not active");
        }
        if (!Objects.equals(userId, battle.getCurrentTurnUserId())) {
            throw new IllegalStateException("Not your turn");
        }

        final int DEFEND_AC_BONUS = 2;
        battle.applyTempAcBonus(userId, DEFEND_AC_BONUS);
        battle.addLog("{USER:" + userId + "} takes a defensive stance (+2 AC until next turn)");

        // Reset last action timestamp
        battle.resetLastActionAt();

        // Persist defend turn
        persistBattleTurn(battle, userId, null, BattleTurn.ActionType.DEFEND,
                         null, null, null, null, 0, false, false);

        // Advance turn
        battle.advanceTurn();

        logger.info("Defend action battleId={} userId={} acBonus={}", battle.getId(), userId, DEFEND_AC_BONUS);
        return new DefendResult(battle, DEFEND_AC_BONUS, null);
    }

    /** Container for defend outcome. */
    public record DefendResult(ActiveBattle battle, int tempAcBonus, String winnerUserId) {}

    /**
     * Perform a spell attack (basic implementation for MVP).
     * Uses INT modifier instead of STR for attack and damage.
     * Creates SPELL turn log entry.
     */
    public SpellResult performSpell(String battleId, String userId, Long abilityId) {
        ActiveBattle battle = getBattleOrThrow(battleId);
        if (!battle.isActive()) {
            throw new IllegalStateException("Battle not active");
        }
        if (!Objects.equals(userId, battle.getCurrentTurnUserId())) {
            throw new IllegalStateException("Not your turn");
        }

        boolean attackerIsChallenger = userId.equals(battle.getChallengerId());
        String defenderUserId = attackerIsChallenger ? battle.getOpponentId() : battle.getChallengerId();

        PlayerCharacter attackerChar = getCharacter(battle.getGuildId(), userId);
        PlayerCharacter defenderChar = getCharacter(battle.getGuildId(), defenderUserId);

        // Get learned abilities for both characters
        List<CharacterAbility> attackerAbilities = characterAbilityRepository.findByCharacter(attackerChar);
        List<CharacterAbility> defenderAbilities = characterAbilityRepository.findByCharacter(defenderChar);

        // Calculate base HP for stats calculation
        int attackerBaseHp = getBaseHpForClass(attackerChar.getCharacterClass());
        int defenderBaseHp = getBaseHpForClass(defenderChar.getCharacterClass());

        // Calculate combat stats with ability bonuses
        CharacterStatsCalculator.CombatStats defenderStats =
            CharacterStatsCalculator.calculateStats(defenderChar, defenderAbilities, defenderBaseHp);

        int attackRoll = battle.rollD20();
        int intToHitMod = abilityMod(attackerChar.getIntelligence());
        int totalAttack = attackRoll + intToHitMod;

        // Apply defender's base AC plus any temporary AC bonus from defend
        int baseArmorClass = defenderStats.armorClass();
        int tempAcBonus = battle.getEffectiveAcBonus(defenderUserId);
        int armorClass = baseArmorClass + tempAcBonus;

        boolean crit = attackRoll >= battleProperties.getCombat().getCrit().getThreshold();
        boolean hit = crit || totalAttack >= armorClass;

        int damage = 0;
        if (hit) {
            int baseSpell = battle.rollD6();
            // For MVP, spell damage = 1d6 + INT modifier
            damage = baseSpell + intToHitMod;
            if (crit) {
                double critMultiplier = battleProperties.getCombat().getCrit().getMultiplier();
                damage = (int) Math.round(damage * critMultiplier);
            }
            damage = Math.max(0, damage); // Prevent negative damage if INT mod < 0
            if (attackerIsChallenger) {
                battle.applyDamageToOpponent(damage);
            } else {
                battle.applyDamageToChallenger(damage);
            }
        }

        StringBuilder logLine = new StringBuilder()
            .append("{USER:").append(userId).append("}").append(" casts a spell (roll=").append(attackRoll)
            .append(", intToHitMod=").append(intToHitMod)
            .append(", total=").append(totalAttack)
            .append(", AC=").append(armorClass);
        if (tempAcBonus > 0) {
            logLine.append(" [+").append(tempAcBonus).append(" defend]");
        }
        logLine.append(") ");
        if (!hit) {
            logLine.append("and misses");
        } else if (crit) {
            logLine.append("CRITS for ").append(damage).append(" dmg");
        } else {
            logLine.append("hits for ").append(damage).append(" dmg");
        }
        battle.addLog(logLine.toString());

        // Reset last action timestamp
        battle.resetLastActionAt();

        // Persist spell turn
        persistBattleTurn(battle, userId, defenderUserId, BattleTurn.ActionType.SPELL,
                         abilityId, attackRoll, totalAttack, armorClass, damage, hit, crit);

        // Check end condition
        boolean ended = battle.getOpponentHp() <= 0 || battle.getChallengerHp() <= 0;
        String winner = null;
        if (ended) {
            winner = battle.getOpponentHp() <= 0 ? battle.getChallengerId() : battle.getOpponentId();
            battle.end(winner);
            recordBattleCompletion(battle.getChallengerId());
            recordBattleCompletion(battle.getOpponentId());
            logger.info("Battle ended (spell) battleId={} winner={} CHP={} OHP={}", battle.getId(), winner, battle.getChallengerHp(), battle.getOpponentHp());
        } else {
            battle.advanceTurn();
        }

        return new SpellResult(battle, attackRoll, totalAttack, armorClass, damage, hit, crit, winner);
    }

    /** Container for spell outcome. */
    public record SpellResult(ActiveBattle battle,
                              int rawRoll,
                              int totalAttack,
                              int defenderAc,
                              int damage,
                              boolean hit,
                              boolean crit,
                              String winnerUserId) {}

    /**
     * Persist a battle turn to the database.
     * Called after each combat action.
     */
    private void persistBattleTurn(ActiveBattle battle,
                                   String actorUserId,
                                   String targetUserId,
                                   BattleTurn.ActionType actionType,
                                   Long abilityId,
                                   Integer rawRoll,
                                   Integer totalRoll,
                                   Integer defenderAc,
                                   int damageDealt,
                                   boolean hit,
                                   boolean crit) {
        BattleTurn turn = new BattleTurn();
        turn.setBattleId(battle.getId());
        turn.setGuildId(battle.getGuildId());
        turn.setTurnNumber(battle.getTurnNumber());
        turn.setActorUserId(actorUserId);
        turn.setActionType(actionType);
        turn.setTargetUserId(targetUserId);
        turn.setAbilityId(abilityId);
        turn.setRawRoll(rawRoll);
        turn.setTotalRoll(totalRoll);
        turn.setDefenderAc(defenderAc);
        turn.setDamageDealt(damageDealt);
        turn.setHit(hit);
        turn.setCrit(crit);

        // Record HP after action
        boolean actorIsChallenger = actorUserId.equals(battle.getChallengerId());
        turn.setHpActorAfter(actorIsChallenger ? battle.getChallengerHp() : battle.getOpponentHp());
        if (targetUserId != null) {
            boolean targetIsChallenger = targetUserId.equals(battle.getChallengerId());
            turn.setHpTargetAfter(targetIsChallenger ? battle.getChallengerHp() : battle.getOpponentHp());
        }

        battleTurnRepository.save(turn);
        logger.debug("Persisted turn {} for battle {} action={}", turn.getTurnNumber(), battle.getId(), actionType);
    }

    /**
     * Record battle completion timestamp for a user.
     * Used for cooldown tracking.
     */
    public void recordBattleCompletion(String userId) {
        battleCooldowns.put(userId, System.currentTimeMillis());
        logger.debug("Recorded battle completion for user {}", userId);
    }

    /**
     * Check if a user is on cooldown based on configured cooldown duration.
     */
    public boolean isOnCooldown(String userId) {
        Long lastCompletionTime = battleCooldowns.getIfPresent(userId);
        if (lastCompletionTime == null) {
            return false;
        }
        long cooldownMs = TimeUnit.SECONDS.toMillis(battleProperties.getCombat().getCooldownSeconds());
        long elapsed = System.currentTimeMillis() - lastCompletionTime;
        return elapsed < cooldownMs;
    }

    /**
     * Get remaining cooldown time in seconds for a user, or 0 if not on cooldown.
     */
    public long getRemainingCooldownSeconds(String userId) {
        Long lastCompletionTime = battleCooldowns.getIfPresent(userId);
        if (lastCompletionTime == null) {
            return 0;
        }
        long cooldownMs = TimeUnit.SECONDS.toMillis(battleProperties.getCombat().getCooldownSeconds());
        long elapsed = System.currentTimeMillis() - lastCompletionTime;
        long remaining = cooldownMs - elapsed;
        return Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remaining));
    }

    /**
     * Check if the current turn has exceeded the turn timeout threshold.
     * Returns true if timeout exceeded, false otherwise.
     */
    public boolean checkTurnTimeout(ActiveBattle battle) {
        if (!battle.isActive() || battle.getLastActionAt() == null) {
            return false;
        }
        long timeoutMs = TimeUnit.SECONDS.toMillis(battleProperties.getCombat().getTurn().getTimeoutSeconds());
        long elapsed = System.currentTimeMillis() - battle.getLastActionAt();
        return elapsed >= timeoutMs;
    }
}
