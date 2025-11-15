package com.discordbot.battle.service;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.AbilityRepository;
import com.discordbot.battle.repository.BattleSessionRepository;
import com.discordbot.battle.repository.BattleTurnRepository;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.Answers;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class BattleServiceTest {

    private PlayerCharacterRepository repo;
    private CharacterAbilityRepository abilityRepo;
    private BattleTurnRepository turnRepo;
    private BattleProperties props;
    private SpellResourceService spellResourceService;
    private AbilityRepository abilityRepository;
    private StatusEffectService statusEffectService;
    private BattleSessionRepository sessionRepository;
    private BattleMetricsService metricsService;
    private MeterRegistry meterRegistry;
    private BattleService service;

    private static final String GUILD = "g1";
    private static final String A = "userA"; // challenger
    private static final String B = "userB"; // opponent

    @BeforeEach
    void setup() {
        repo = mock(PlayerCharacterRepository.class);
        abilityRepo = mock(CharacterAbilityRepository.class);
        turnRepo = mock(BattleTurnRepository.class);
        props = new BattleProperties();
        props.setEnabled(true);
        // Crits: nat 20, x2 damage
        props.getCombat().getCrit().setThreshold(20);
        props.getCombat().getCrit().setMultiplier(2.0);
        // Base HP (D&D 5e hit die maximums)
        props.getClassConfig().getWarrior().setBaseHp(10);
        props.getClassConfig().getRogue().setBaseHp(8);
        props.getClassConfig().getMage().setBaseHp(6);
        props.getClassConfig().getCleric().setBaseHp(8);

        // Mock Phase 4-9 dependencies
        spellResourceService = mock(SpellResourceService.class);
        abilityRepository = mock(AbilityRepository.class);
        statusEffectService = mock(StatusEffectService.class);
        sessionRepository = mock(BattleSessionRepository.class);
        metricsService = mock(BattleMetricsService.class);
        // Phase 9: Use RETURNS_DEEP_STUBS for MeterRegistry to support CaffeineCacheMetrics.monitor() calls
        meterRegistry = mock(MeterRegistry.class, Answers.RETURNS_DEEP_STUBS);

        // Mock: No abilities learned by default
        when(abilityRepo.findByCharacter(any())).thenReturn(List.of());

        // Mock status effect processing to return empty result (no effects)
        when(statusEffectService.processTurnStartEffects(any(), any()))
            .thenReturn(new StatusEffectService.TurnStartEffectResult(0, 0, "", false));
        // Mock damage modifiers to return 100% (no modification)
        when(statusEffectService.getDamageModifierPercent(any(), any())).thenReturn(100);
        when(statusEffectService.getIncomingDamageModifierPercent(any(), any())).thenReturn(100);
        // Mock shield to return 0 (no shield)
        when(statusEffectService.getShieldValue(any(), any())).thenReturn(0);

        service = new BattleService(
            repo,
            abilityRepo,
            turnRepo,
            props,
            spellResourceService,
            abilityRepository,
            statusEffectService,
            sessionRepository,
            metricsService,
            meterRegistry
        );
    }


    private void mockChars(String aClass, int aStr, int aDex, int aCon,
                           String bClass, int bStr, int bDex, int bCon) {
        PlayerCharacter pa = new PlayerCharacter(A, GUILD, aClass, "Human",
                aStr, aDex, aCon, 10, 10, 10);
        PlayerCharacter pb = new PlayerCharacter(B, GUILD, bClass, "Human",
                bStr, bDex, bCon, 10, 10, 10);

        // Set IDs using reflection (needed for cache key in Phase 9)
        try {
            java.lang.reflect.Field idFieldA = PlayerCharacter.class.getDeclaredField("id");
            idFieldA.setAccessible(true);
            idFieldA.set(pa, 1L);

            java.lang.reflect.Field idFieldB = PlayerCharacter.class.getDeclaredField("id");
            idFieldB.setAccessible(true);
            idFieldB.set(pb, 2L);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set character IDs", e);
        }

        when(repo.findByUserIdAndGuildId(A, GUILD)).thenReturn(Optional.of(pa));
        when(repo.findByUserIdAndGuildId(B, GUILD)).thenReturn(Optional.of(pb));
    }

    private ActiveBattle startBattle(IntSupplier d20, IntSupplier d6) {
        var battle = service.createChallenge(GUILD, A, B);
        service.acceptChallenge(battle.getId(), B);
        // Inject deterministic dice
        var active = service.getBattleOrThrow(battle.getId());
        active.setTestDiceSuppliers(d20, d6);
        return active;
    }

    @Test
    void attack_miss_when_total_below_ac() {
        // Attacker STR 8 (-1), Defender DEX 15 (+2) => AC 12
        mockChars("Warrior", 8, 10, 10, "Mage", 10, 15, 10);
        ActiveBattle battle = startBattle(() -> 1, () -> 3); // roll 1 always

        var result = service.performAttack(battle.getId(), A);
        assertFalse(result.hit());
        assertEquals(ActiveBattle.BattleStatus.ACTIVE, result.battle().getStatus());
        // Turn should advance to B on miss
        assertEquals(B, result.battle().getCurrentTurnUserId());
        // No damage applied
        assertEquals(result.battle().getOpponentHp(), battle.getOpponentHp());
    }

    @Test
    void attack_hit_when_total_meets_ac() {
        // Attacker STR 10 (+0), Defender DEX 10 (+0) => AC 10; roll 10
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10);
        ActiveBattle battle = startBattle(() -> 10, () -> 4); // attack 10, damage die 4

        int before = battle.getOpponentHp();
        var result = service.performAttack(battle.getId(), A);
        assertTrue(result.hit());
        assertFalse(result.crit());
        assertEquals(4, result.damage());
        assertEquals(before - 4, result.battle().getOpponentHp());
        assertNull(result.winnerUserId());
        assertEquals(B, result.battle().getCurrentTurnUserId());
    }

    @Test
    void attack_crit_applies_multiplier() {
        // Attacker STR 15 (+2), Defender DEX 10 (+0), roll 20 crit, d6=3 => (3+2)*2=10
        // Use a higher-HP defender to avoid accidental lethal so we can assert exact HP delta
        mockChars("Warrior", 15, 10, 10, "Warrior", 10, 10, 10);
        ActiveBattle battle = startBattle(() -> 20, () -> 3);

        int before = battle.getOpponentHp();
        var result = service.performAttack(battle.getId(), A);
        assertTrue(result.hit());
        assertTrue(result.crit());
        assertEquals(10, result.damage());
        assertEquals(before - 10, result.battle().getOpponentHp());
    }

    @Test
    void attack_that_reduces_hp_to_zero_ends_battle_and_sets_winner() {
        // Opponent low HP: Mage base 6 + CON 8 (-1) => 5 HP
        // Attacker crit does 10 damage as above to ensure lethal
        mockChars("Warrior", 15, 10, 10, "Mage", 10, 10, 8);
        ActiveBattle battle = startBattle(() -> 20, () -> 3);

        var result = service.performAttack(battle.getId(), A);
        assertNotNull(result.winnerUserId());
        assertEquals(A, result.winnerUserId());
        assertEquals(ActiveBattle.BattleStatus.ENDED, result.battle().getStatus());
    }

    @Test
    void damage_never_negative_with_low_str_and_low_roll() {
        // STR 8 (-1) attacker vs opponent with arbitrary stats; damage die=1 => base 1-1=0 floor
        mockChars("Warrior", 8, 10, 10, "Mage", 10, 10, 10);
        ActiveBattle battle = startBattle(() -> 15, () -> 1); // ensure a hit (15 + -1 vs AC 10)
        int before = battle.getOpponentHp();
        var result = service.performAttack(battle.getId(), A);
        assertTrue(result.hit());
        assertEquals(0, result.damage());
        assertEquals(before, battle.getOpponentHp() - result.damage()); // HP reduced by 0
    }

    @Test
    void cannot_attack_out_of_turn() {
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10);
        ActiveBattle battle = startBattle(() -> 10, () -> 4);
        // Challenger (A) attacks first successfully
        service.performAttack(battle.getId(), A);
        // Now it's B's turn; A attempting again should throw
        assertThrows(IllegalStateException.class, () -> service.performAttack(battle.getId(), A));
    }

    @Test
    void duplicate_challenge_prevented_even_reversed_order() {
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10);
        service.createChallenge(GUILD, A, B);
        assertThrows(IllegalStateException.class, () -> service.createChallenge(GUILD, B, A));
    }

    @Test
    void user_cannot_be_in_two_battles() {
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10);
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10); // reuse mock
        service.createChallenge(GUILD, A, B);
        // A tries to challenge someone else (new opponent C) - need to mock C character
        PlayerCharacter pcC = new PlayerCharacter("userC", GUILD, "Rogue", "Human", 10, 10, 10, 10, 10, 10);
        when(repo.findByUserIdAndGuildId("userC", GUILD)).thenReturn(Optional.of(pcC));
        assertThrows(IllegalStateException.class, () -> service.createChallenge(GUILD, A, "userC"));
    }

    @Test
    void pending_challenge_expires_and_cannot_be_accepted() throws InterruptedException {
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10);
        // Set very short expiration
        props.getChallenge().setExpireSeconds(0); // immediate expiry
        ActiveBattle battle = service.createChallenge(GUILD, A, B);
        // Attempt accept should fail due to expiry
        assertThrows(IllegalStateException.class, () -> service.acceptChallenge(battle.getId(), B));
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = java.util.concurrent.TimeUnit.SECONDS)
    void concurrent_challenges_only_one_created() throws InterruptedException {
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10);

        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(2);
        final java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger();

        Runnable task = () -> {
            try {
                start.await();
                service.createChallenge(GUILD, A, B);
                successes.incrementAndGet();
            } catch (IllegalStateException expected) {
                // one of the threads should hit duplicate/user busy and throw
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted");
            } finally {
                done.countDown();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        start.countDown();
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS), "Threads should complete within 5 seconds");

        assertEquals(1, successes.get(), "Exactly one challenge should be created under contention");
        assertTrue(service.findExistingBetween(A, B).isPresent());
    }

    @Test
    void missing_challenger_character_yields_user_friendly_message() {
        // Challenger missing
        when(repo.findByUserIdAndGuildId(A, GUILD)).thenReturn(Optional.empty());
        // Opponent present
        PlayerCharacter pb = new PlayerCharacter(B, GUILD, "Mage", "Human", 10, 10, 10, 10, 10, 10);
        when(repo.findByUserIdAndGuildId(B, GUILD)).thenReturn(Optional.of(pb));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.createChallenge(GUILD, A, B));
        assertTrue(ex.getMessage().toLowerCase().contains("create a character"));
    }

    @Test
    void challenge_allowed_if_opponent_missing_character() {
        // Challenger present, opponent missing
        PlayerCharacter pa = new PlayerCharacter(A, GUILD, "Warrior", "Human", 10, 10, 10, 10, 10, 10);
        when(repo.findByUserIdAndGuildId(A, GUILD)).thenReturn(Optional.of(pa));
        when(repo.findByUserIdAndGuildId(B, GUILD)).thenReturn(Optional.empty());
        ActiveBattle battle = service.createChallenge(GUILD, A, B);
        assertNotNull(battle);
        assertTrue(battle.isPending());
    }

    @Test
    void accept_requires_opponent_character_then_succeeds_after_creation() {
        // Challenger present, opponent initially missing
        PlayerCharacter pa = new PlayerCharacter(A, GUILD, "Warrior", "Human", 10, 10, 10, 10, 10, 10);
        when(repo.findByUserIdAndGuildId(A, GUILD)).thenReturn(Optional.of(pa));
        when(repo.findByUserIdAndGuildId(B, GUILD)).thenReturn(Optional.empty());
        ActiveBattle battle = service.createChallenge(GUILD, A, B);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.acceptChallenge(battle.getId(), B));
        assertTrue(ex.getMessage().toLowerCase().contains("create a character"));
        // Now opponent creates a character
        PlayerCharacter pb = new PlayerCharacter(B, GUILD, "Mage", "Human", 10, 10, 10, 10, 10, 10);
        when(repo.findByUserIdAndGuildId(B, GUILD)).thenReturn(Optional.of(pb));
        ActiveBattle active = service.acceptChallenge(battle.getId(), B);
        assertTrue(active.isActive());
    }

    @Test
    void attack_log_contains_user_marker_tag() {
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10);
        ActiveBattle battle = startBattle(() -> 5, () -> 3); // ensures not crit; may hit or miss

        service.performAttack(battle.getId(), A);
        var entries = battle.getLogEntries();
        assertFalse(entries.isEmpty());
        String last = entries.get(entries.size() - 1).getMessage();
        assertTrue(last.contains("{USER:" + A + "}"), "Log should contain explicit {USER:id} marker");
    }

    @Test
    void base_hp_negative_config_is_sanitized_to_minimum() {
        // Configure invalid negative base HP for Warrior and ensure computeStartingHp yields >=1
        props.getClassConfig().getWarrior().setBaseHp(-5);
        // Challenger Warrior with CON 8 (-1), Opponent Mage normal
        mockChars("Warrior", 10, 10, 8, "Mage", 10, 10, 10);
        var pending = service.createChallenge(GUILD, A, B);
        var active = service.acceptChallenge(pending.getId(), B);
        // After start, challenger HP should be at least 1 (sanitized), with given values equals 1
        assertEquals(1, active.getChallengerHp());
    }

    @Test
    void cleanupUnusedLocks_removes_locks_for_users_not_in_active_battles() {
        // Setup: Create and end a battle, verify locks are present, then cleanup
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10);
        
        // Create a challenge which populates userLocks for A and B
        var battle1 = service.createChallenge(GUILD, A, B);
        
        // Verify locks exist via reflection (since userLocks is private)
        var locksField = assertDoesNotThrow(() -> BattleService.class.getDeclaredField("userLocks"));
        locksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var locks = assertDoesNotThrow(() -> (java.util.concurrent.ConcurrentHashMap<String, Object>) locksField.get(service));
        
        assertTrue(locks.containsKey(A), "Lock for user A should exist after challenge");
        assertTrue(locks.containsKey(B), "Lock for user B should exist after challenge");
        
        service.acceptChallenge(battle1.getId(), B);
        
        // End the battle by attacking until someone wins
        var active = service.getBattleOrThrow(battle1.getId());
        active.setTestDiceSuppliers(() -> 20, () -> 6); // Max damage to finish quickly
        while (!active.isEnded()) {
            String current = active.getCurrentTurnUserId();
            service.performAttack(battle1.getId(), current);
        }
        
        // Now run cleanup - should remove locks since no active/pending battles remain
        service.cleanUpExpiredChallenges();
        
        assertFalse(locks.containsKey(A), "Lock for user A should be cleaned up after battle ends");
        assertFalse(locks.containsKey(B), "Lock for user B should be cleaned up after battle ends");
    }

    @Test
    void cleanupUnusedLocks_preserves_locks_for_users_in_active_battles() {
        // Create two battles: one active, one ended
        mockChars("Warrior", 10, 10, 10, "Mage", 10, 10, 10);

        // Battle 1: A vs B - will stay active
        var battle1 = service.createChallenge(GUILD, A, B);
        service.acceptChallenge(battle1.getId(), B);

        // Battle 2: A vs C - will be ended (need to mock C)
        PlayerCharacter pcC = new PlayerCharacter("userC", GUILD, "Rogue", "Human", 10, 10, 10, 10, 10, 10);
        when(repo.findByUserIdAndGuildId("userC", GUILD)).thenReturn(Optional.of(pcC));

        // End battle1 to free up A for another challenge
        var active1 = service.getBattleOrThrow(battle1.getId());
        active1.setTestDiceSuppliers(() -> 20, () -> 6);
        while (!active1.isEnded()) {
            service.performAttack(battle1.getId(), active1.getCurrentTurnUserId());
        }

        // Clear cooldowns so A can immediately start battle2 (test is about lock cleanup, not cooldowns)
        try {
            var cooldownField = BattleService.class.getDeclaredField("battleCooldowns");
            cooldownField.setAccessible(true);
            var cooldowns = (com.github.benmanes.caffeine.cache.Cache<String, Long>) cooldownField.get(service);
            cooldowns.invalidateAll();
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear cooldowns", e);
        }

        // Now A can start a new battle with C
        var battle2 = service.createChallenge(GUILD, A, "userC");
        service.acceptChallenge(battle2.getId(), "userC");

        // Get locks map via reflection
        var locksField = assertDoesNotThrow(() -> BattleService.class.getDeclaredField("userLocks"));
        locksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var locks = assertDoesNotThrow(() -> (java.util.concurrent.ConcurrentHashMap<String, Object>) locksField.get(service));

        // Run cleanup
        service.cleanUpExpiredChallenges();

        // A and C should still have locks (active battle), B should not (no active battles)
        assertTrue(locks.containsKey(A), "Lock for user A should remain (active in battle2)");
        assertTrue(locks.containsKey("userC"), "Lock for user C should remain (active in battle2)");
        assertFalse(locks.containsKey(B), "Lock for user B should be cleaned up (no active battles)");
    }

    @Test
    void attack_with_learned_abilities_applies_bonuses() {
        // Test that learned abilities with effect bonuses correctly influence combat
        // Attacker (Warrior): STR 12 (+1), learns ability with "DAMAGE+3,AC+2,CRIT_DAMAGE+10"
        // Defender (Mage): DEX 10 (+0), no abilities
        mockChars("Warrior", 12, 10, 10, "Mage", 10, 10, 10);
        
        // Mock learned abilities for attacker (user A)
        PlayerCharacter attackerChar = repo.findByUserIdAndGuildId(A, GUILD).get();
        var mockAbility = mock(com.discordbot.battle.entity.CharacterAbility.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(mockAbility.getAbility().getEffect()).thenReturn("DAMAGE+3,AC+2,CRIT_DAMAGE+10");
        when(abilityRepo.findByCharacter(attackerChar)).thenReturn(List.of(mockAbility));
        
        // Defender has no learned abilities (already mocked to return empty list)
        
        ActiveBattle battle = startBattle(() -> 15, () -> 4); // non-crit hit
        
        var result = service.performAttack(battle.getId(), A);
        
        assertTrue(result.hit());
        assertFalse(result.crit());
        
        // Expected damage: d6(4) + STR mod(+1) + DAMAGE bonus(+3) = 8
        // Defender (Mage) starts with baseHp=6 + CON mod(0) = 6 HP
        // After 8 damage: HP would be -2, but clamped to minimum 0
        assertEquals(8, result.damage());
        assertEquals(0, result.battle().getOpponentHp()); // HP cannot go below 0
    }
    
    @Test
    void attack_with_crit_damage_bonus_applies_increased_multiplier() {
        // Test that CRIT_DAMAGE bonuses increase crit multiplier
        // Attacker with CRIT_DAMAGE+20 (base 2.0x + 0.2 = 2.2x)
        mockChars("Rogue", 14, 10, 10, "Warrior", 10, 10, 10);
        
        PlayerCharacter attackerChar = repo.findByUserIdAndGuildId(A, GUILD).get();
        var mockAbility = mock(com.discordbot.battle.entity.CharacterAbility.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(mockAbility.getAbility().getEffect()).thenReturn("CRIT_DAMAGE+20");
        when(abilityRepo.findByCharacter(attackerChar)).thenReturn(List.of(mockAbility));
        
        ActiveBattle battle = startBattle(() -> 20, () -> 5); // nat 20 crit, d6=5
        
        var result = service.performAttack(battle.getId(), A);
        
        assertTrue(result.hit());
        assertTrue(result.crit());
        
        // Base damage: 5 + STR mod(+2) = 7
        // Crit multiplier: 2.0 + (20/100) = 2.2
        // Expected: 7 * 2.2 = 15.4 → 15 (rounded)
        assertEquals(15, result.damage());
    }
    
    @Test
    void defender_with_ac_bonus_harder_to_hit() {
        // Test that AC bonuses from abilities make defender harder to hit
        // Attacker: STR 10 (+0), proficiency +2
        // Defender: DEX 10 (+0), AC+3 bonus → AC = 10 + 0 + 3 = 13
        mockChars("Warrior", 10, 10, 10, "Cleric", 10, 10, 10);

        var mockAbility = mock(com.discordbot.battle.entity.CharacterAbility.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(mockAbility.getAbility().getEffect()).thenReturn("AC+3");
        // Match any character with userId = B (defender)
        when(abilityRepo.findByCharacter(argThat(pc -> pc != null && B.equals(pc.getUserId()))))
            .thenReturn(List.of(mockAbility));

        ActiveBattle battle = startBattle(() -> 10, () -> 4); // roll 10

        // Attack roll: 10 + proficiency(+2) + STR mod(+0) = 12 < AC 13 (MISS)
        var result = service.performAttack(battle.getId(), A);

        assertFalse(result.hit());
        assertEquals(0, result.damage());
    }
}
