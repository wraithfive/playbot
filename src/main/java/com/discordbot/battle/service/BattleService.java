package com.discordbot.battle.service;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
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
    private static final int FALLBACK_BASE_HP = 8;
    private static final int MINIMUM_HP = 1;
    private static final int BASE_ARMOR_CLASS = 10;
    private static final int ABILITY_SCORE_BASE = 10;
    private static final int ABILITY_MODIFIER_DIVISOR = 2;

    private final PlayerCharacterRepository characterRepository;
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

    // Per-user lightweight monitor objects to prevent race conditions when issuing challenges.
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    public BattleService(PlayerCharacterRepository characterRepository, BattleProperties battleProperties) {
        this.characterRepository = characterRepository;
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

        int attackRoll = battle.rollD20();
        int strMod = abilityMod(attackerChar.getStrength());
        int dexDefMod = abilityMod(defenderChar.getDexterity());
        int totalAttack = attackRoll + strMod;
        int armorClass = BASE_ARMOR_CLASS + dexDefMod; // MVP AC formula

        boolean crit = attackRoll >= battleProperties.getCombat().getCrit().getThreshold();
        boolean hit = crit || totalAttack >= armorClass;

        int damage = 0;
        if (hit) {
            int baseWeapon = battle.rollD6();
            damage = baseWeapon + strMod;
            if (crit) {
                damage = (int) Math.round(damage * battleProperties.getCombat().getCrit().getMultiplier());
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
            .append(", strMod=").append(strMod)
            .append(", total=").append(totalAttack)
            .append(", AC=").append(armorClass)
            .append(") ");
        if (!hit) {
            logLine.append("and misses");
        } else if (crit) {
            logLine.append("CRITS for ").append(damage).append(" dmg");
        } else {
            logLine.append("hits for ").append(damage).append(" dmg");
        }
        battle.addLog(logLine.toString());

        // Check end condition
        boolean ended = battle.getOpponentHp() <= 0 || battle.getChallengerHp() <= 0;
        String winner = null;
        if (ended) {
            winner = battle.getOpponentHp() <= 0 ? battle.getChallengerId() : battle.getOpponentId();
            battle.end(winner);
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

    /** Compute HP: base class HP + CON modifier (level 1). */
    private int computeStartingHp(String guildId, String userId) {
        PlayerCharacter pc = getCharacter(guildId, userId);
        int baseHp = switch (pc.getCharacterClass().toLowerCase()) {
            case "warrior" -> battleProperties.getClassConfig().getWarrior().getBaseHp();
            case "rogue" -> battleProperties.getClassConfig().getRogue().getBaseHp();
            case "mage" -> battleProperties.getClassConfig().getMage().getBaseHp();
            case "cleric" -> battleProperties.getClassConfig().getCleric().getBaseHp();
            default -> FALLBACK_BASE_HP; // fallback for unknown classes
        };
        if (baseHp < MINIMUM_HP) {
            logger.error("Configured base HP {} is invalid (<{}) for class {}; defaulting to {}", 
                baseHp, MINIMUM_HP, pc.getCharacterClass(), MINIMUM_HP);
            baseHp = MINIMUM_HP;
        }
        int conMod = abilityMod(pc.getConstitution());
        return Math.max(MINIMUM_HP, baseHp + conMod); // minimum 1 HP safeguard
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
}
