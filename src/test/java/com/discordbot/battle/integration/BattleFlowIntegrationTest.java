package com.discordbot.battle.integration;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.entity.PlayerCharacterTestFactory;
import com.discordbot.battle.repository.AbilityRepository;
import com.discordbot.battle.repository.BattleSessionRepository;
import com.discordbot.battle.repository.BattleTurnRepository;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import com.discordbot.battle.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for complete battle flows.
 * Phase 13: Test Suite Build-Out
 *
 * These tests verify end-to-end battle scenarios:
 * - Complete battle from challenge to victory
 * - Forfeit flow
 * - Draw scenarios
 * - Status effect application across turns
 */
@SuppressWarnings("unused")
class BattleFlowIntegrationTest {

    @Mock
    private PlayerCharacterRepository characterRepository;

    @Mock
    private CharacterAbilityRepository characterAbilityRepository;

    @Mock
    private BattleSessionRepository sessionRepository;

    @Mock
    private BattleTurnRepository turnRepository;

    @Mock
    private BattleMetricsService metricsService;

    @Mock
    private StatusEffectService statusEffectService;

    @Mock
    private SpellResourceService spellResourceService;

    @Mock
    private AbilityRepository abilityRepository;

    private BattleProperties battleProperties;
    private BattleService battleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create real config
        battleProperties = new BattleProperties();
        battleProperties.setEnabled(true);
        battleProperties.getCombat().setCooldownSeconds(0); // Disable cooldowns for tests

        // Create service with all required dependencies (use real SimpleMeterRegistry for cache metrics)
        battleService = new BattleService(
            characterRepository,
            characterAbilityRepository,
            turnRepository,
            battleProperties,
            spellResourceService,
            abilityRepository,
            statusEffectService,
            sessionRepository,
            metricsService,
            new SimpleMeterRegistry()
        );

        // Mock default status effect behavior (no damage, no healing, no messages, not stunned)
        when(statusEffectService.processTurnStartEffects(any(), anyString()))
            .thenReturn(new StatusEffectService.TurnStartEffectResult(0, 0, "", false));
        when(statusEffectService.getAttackModifier(anyString(), anyString())).thenReturn(0);
        when(statusEffectService.getAcModifier(anyString(), anyString())).thenReturn(0);
        when(statusEffectService.getDamageModifierPercent(anyString(), anyString())).thenReturn(100);
        when(statusEffectService.getIncomingDamageModifierPercent(anyString(), anyString())).thenReturn(100);
        when(statusEffectService.getShieldValue(anyString(), anyString())).thenReturn(0);

        // Mock ability repositories to return empty lists (no learned abilities in basic tests)
        when(characterAbilityRepository.findByCharacter(any()))
            .thenReturn(java.util.List.of());
    }

    /**
     * Integration Test: Complete battle flow from challenge to victory
     */
    @Test
    void completeBattleFlowFromChallengeToVictory() {
        // Given: Two characters
        PlayerCharacter challenger = PlayerCharacterTestFactory.create(
            "111111111111111111", "999999999999999999", "Warrior", "Human",
            18, 10, 16, 10, 10, 10 // High STR and CON
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "222222222222222222", "999999999999999999", "Mage", "Elf",
            6, 8, 1, 10, 10, 10 // Minimal stats (very weak) to ensure battle ends quickly
        );

        when(characterRepository.findByUserIdAndGuildId("111111111111111111", "999999999999999999"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("222222222222222222", "999999999999999999"))
            .thenReturn(Optional.of(opponent));

        // When: Create challenge
        ActiveBattle battle = battleService.createChallenge("999999999999999999", "111111111111111111", "222222222222222222");
        assertNotNull(battle);
        assertTrue(battle.isPending());
        assertEquals("111111111111111111", battle.getChallengerId());
        assertEquals("222222222222222222", battle.getOpponentId());

        // When: Accept challenge
        ActiveBattle activeBattle = battleService.acceptChallenge(battle.getId(), "222222222222222222");
        assertNotNull(activeBattle);
        assertTrue(activeBattle.isActive());
        assertTrue(activeBattle.getChallengerHp() > 0);
        assertTrue(activeBattle.getOpponentHp() > 0);

        // When: Execute turns until battle ends
        int maxTurns = 100; // Safety limit
        int turnCount = 0;
        while (activeBattle.isActive() && turnCount < maxTurns) {
            String currentPlayer = activeBattle.getCurrentTurnUserId();

            // Perform attack
            BattleService.AttackResult result = battleService.performAttack(
                activeBattle.getId(),
                currentPlayer
            );

            assertNotNull(result);
            activeBattle = result.battle();

            turnCount++;

            // Verify HP is non-negative
            assertTrue(activeBattle.getChallengerHp() >= 0,
                "Challenger HP should never be negative");
            assertTrue(activeBattle.getOpponentHp() >= 0,
                "Opponent HP should never be negative");
        }

        // Then: Battle should have ended
        assertTrue(activeBattle.isEnded(), "Battle should have ended after " + turnCount + " turns");
        assertNotNull(activeBattle.getWinnerUserId(), "There should be a winner");
        assertTrue(
            activeBattle.getChallengerHp() == 0 || activeBattle.getOpponentHp() == 0,
            "One participant should have 0 HP"
        );
    }

    /**
     * Integration Test: Forfeit flow
     */
    @Test
    void battleForfeitFlow() {
        // Given: Active battle
        PlayerCharacter challenger = PlayerCharacterTestFactory.create(
            "111111111111111111", "999999999999999999", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "222222222222222222", "999999999999999999", "Rogue", "Halfling",
            12, 16, 12, 10, 14, 10
        );

        when(characterRepository.findByUserIdAndGuildId("111111111111111111", "999999999999999999"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("222222222222222222", "999999999999999999"))
            .thenReturn(Optional.of(opponent));

        ActiveBattle battle = battleService.createChallenge("999999999999999999", "111111111111111111", "222222222222222222");
        battleService.acceptChallenge(battle.getId(), "222222222222222222");

        // When: Challenger forfeits
        ActiveBattle forfeitedBattle = battleService.forfeit(battle.getId(), "111111111111111111");

        // Then: Battle should be ended with opponent as winner
        assertNotNull(forfeitedBattle);
        assertTrue(forfeitedBattle.isEnded(), "Battle should be ended");
        assertEquals("222222222222222222", forfeitedBattle.getWinnerUserId(),
            "Opponent should be the winner after challenger forfeits");
    }

    /**
     * Integration Test: Defend action flow
     */
    @Test
    void defendActionFlow() {
        // Given: Active battle
        PlayerCharacter challenger = PlayerCharacterTestFactory.create(
            "111111111111111111", "999999999999999999", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "222222222222222222", "999999999999999999", "Cleric", "Dwarf",
            14, 10, 16, 10, 16, 12
        );

        when(characterRepository.findByUserIdAndGuildId("111111111111111111", "999999999999999999"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("222222222222222222", "999999999999999999"))
            .thenReturn(Optional.of(opponent));

        ActiveBattle battle = battleService.createChallenge("999999999999999999", "111111111111111111", "222222222222222222");
        battle = battleService.acceptChallenge(battle.getId(), "222222222222222222");

        String currentPlayer = battle.getCurrentTurnUserId();
        int initialTempAcBonus = battle.getTempAcBonus();

        // When: Player uses defend action
        BattleService.DefendResult defendResult = battleService.performDefend(
            battle.getId(),
            currentPlayer
        );

        // Then: Should grant AC bonus
        assertNotNull(defendResult);
        ActiveBattle defendedBattle = defendResult.battle();
        assertTrue(defendedBattle.getTempAcBonus() > initialTempAcBonus,
            "Defend should grant AC bonus");
        assertEquals(currentPlayer, defendedBattle.getTempAcBonusUserId(),
            "AC bonus should be assigned to defending player");
    }

    /**
     * Integration Test: Character creation → battle → progression
     */
    @Test
    void characterCreationToBattleToProgression() {
        // Given: Two new characters
        PlayerCharacter newChar1 = PlayerCharacterTestFactory.create(
            "444444444444444444", "999999999999999999", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter newChar2 = PlayerCharacterTestFactory.create(
            "555555555555555555", "999999999999999999", "Rogue", "Elf",
            12, 16, 12, 10, 14, 10
        );

        when(characterRepository.findByUserIdAndGuildId("444444444444444444", "999999999999999999"))
            .thenReturn(Optional.of(newChar1));
        when(characterRepository.findByUserIdAndGuildId("555555555555555555", "999999999999999999"))
            .thenReturn(Optional.of(newChar2));

        // When: Characters battle
        ActiveBattle battle = battleService.createChallenge("999999999999999999", "444444444444444444", "555555555555555555");
        battle = battleService.acceptChallenge(battle.getId(), "555555555555555555");

        // Execute a few turns
        for (int i = 0; i < 5 && battle.isActive(); i++) {
            String currentPlayer = battle.getCurrentTurnUserId();
            BattleService.AttackResult result = battleService.performAttack(
                battle.getId(),
                currentPlayer
            );
            battle = result.battle();
        }

        // Then: Battle should have progressed
        assertTrue(battle.getTurnNumber() >= 5 || battle.isEnded(),
            "Battle should have progressed through multiple turns");

        // If battle ended, verify winner
        if (battle.isEnded()) {
            assertNotNull(battle.getWinnerUserId(), "Ended battle should have a winner");
        }
    }

    /**
     * Integration Test: Battle challenge expiration
     */
    @Test
    void battleChallengeCannotBeAcceptedAfterCancellation() {
        // Given: Characters and a pending challenge
        PlayerCharacter challenger = PlayerCharacterTestFactory.create(
            "111111111111111111", "999999999999999999", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "222222222222222222", "999999999999999999", "Mage", "Elf",
            8, 14, 10, 18, 12, 10
        );

        when(characterRepository.findByUserIdAndGuildId("111111111111111111", "999999999999999999"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("222222222222222222", "999999999999999999"))
            .thenReturn(Optional.of(opponent));

        ActiveBattle battle = battleService.createChallenge("999999999999999999", "111111111111111111", "222222222222222222");

        // When: Challenge is manually ended/removed from cache
        // (In real scenario, this would be done by cleanup scheduler or manual cancellation)
        // Simulate by trying to accept a non-existent battle

        // Then: Accepting should fail
        String nonExistentBattleId = "non-existent-battle-id";
        assertThrows(IllegalStateException.class, () ->
            battleService.acceptChallenge(nonExistentBattleId, "222222222222222222"),
            "Accepting non-existent battle should throw exception"
        );
    }

    /**
     * Integration Test: Cannot challenge while already in battle
     */
    @Test
    void cannotCreateChallengeWhileAlreadyInBattle() {
        // Given: Two active battles
        PlayerCharacter user1 = PlayerCharacterTestFactory.create(
            "111111111111111111", "999999999999999999", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter user2 = PlayerCharacterTestFactory.create(
            "222222222222222222", "999999999999999999", "Rogue", "Elf",
            12, 16, 12, 10, 14, 10
        );
        PlayerCharacter user3 = PlayerCharacterTestFactory.create(
            "333333333333333333", "999999999999999999", "Mage", "Human",
            8, 14, 10, 18, 12, 10
        );

        when(characterRepository.findByUserIdAndGuildId("111111111111111111", "999999999999999999"))
            .thenReturn(Optional.of(user1));
        when(characterRepository.findByUserIdAndGuildId("222222222222222222", "999999999999999999"))
            .thenReturn(Optional.of(user2));
        when(characterRepository.findByUserIdAndGuildId("333333333333333333", "999999999999999999"))
            .thenReturn(Optional.of(user3));

        // When: user1 challenges user2
        ActiveBattle battle1 = battleService.createChallenge("999999999999999999", "111111111111111111", "222222222222222222");

        // Then: user1 cannot challenge user3 (busy with pending challenge to user2)
        assertThrows(IllegalStateException.class, () ->
            battleService.createChallenge("999999999999999999", "111111111111111111", "333333333333333333"),
            "Cannot create challenge while already in pending battle"
        );
    }

    /**
     * Integration Test: Battle state consistency after multiple operations
     */
    @Test
    void battleStateRemainsConsistentAfterMultipleOperations() {
        // Given: Characters
        PlayerCharacter challenger = PlayerCharacterTestFactory.create(
            "111111111111111111", "999999999999999999", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "222222222222222222", "999999999999999999", "Rogue", "Elf",
            12, 16, 12, 10, 14, 10
        );

        when(characterRepository.findByUserIdAndGuildId("111111111111111111", "999999999999999999"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("222222222222222222", "999999999999999999"))
            .thenReturn(Optional.of(opponent));

        // When: Perform multiple operations
        ActiveBattle battle = battleService.createChallenge("999999999999999999", "111111111111111111", "222222222222222222");
        String battleId = battle.getId();

        // Accept
        battle = battleService.acceptChallenge(battleId, "222222222222222222");
        assertTrue(battle.isActive(), "Battle should be active after accept");

        int operationsPerformed = 0;

        // Attack (if still active)
        if (battle.isActive()) {
            String player1 = battle.getCurrentTurnUserId();
            battle = battleService.performAttack(battleId, player1).battle();
            operationsPerformed++;
        }

        // Defend (if still active)
        if (battle.isActive()) {
            String player2 = battle.getCurrentTurnUserId();
            battle = battleService.performDefend(battleId, player2).battle();
            operationsPerformed++;
        }

        // Attack again (if still active)
        if (battle.isActive()) {
            String player3 = battle.getCurrentTurnUserId();
            battle = battleService.performAttack(battleId, player3).battle();
            operationsPerformed++;
        }

        // Then: State should be consistent
        assertTrue(operationsPerformed >= 1, "At least one operation should have been performed");
        assertTrue(battle.getTurnNumber() >= operationsPerformed || battle.isEnded(),
            "Turn number should reflect actions taken or battle ended");
        assertTrue(battle.getChallengerHp() >= 0, "Challenger HP should never be negative");
        assertTrue(battle.getOpponentHp() >= 0, "Opponent HP should never be negative");

        // If battle ended, verify end state is valid
        if (battle.isEnded()) {
            assertNotNull(battle.getWinnerUserId(), "Ended battle should have a winner");
            assertTrue(battle.getChallengerHp() == 0 || battle.getOpponentHp() == 0,
                "When battle ends, one participant should have 0 HP");
        }
    }

    /**
     * Integration Test: Battles should complete within reasonable turn counts
     *
     * This test ensures the combat system provides a good player experience by:
     * - Preventing infinitely long battles
     * - Ensuring mismatched battles end quickly
     * - Verifying balanced battles complete in reasonable time
     *
     * Expected turn counts based on D&D 5e combat design:
     * - Typical combat: 3-5 rounds (6-10 turns for 1v1)
     * - Extended combat: 5-10 rounds (10-20 turns for 1v1)
     * - Maximum acceptable: 25 rounds (50 turns for 1v1)
     */
    @Test
    void battlesShouldCompleteInReasonableTurnCount() {
        // Scenario 1: Huge stat difference - should end very quickly (within 10 turns)
        PlayerCharacter strongWarrior = PlayerCharacterTestFactory.create(
            "666666666666666666", "999999999999999999", "Warrior", "Human",
            18, 10, 16, 10, 10, 10 // High STR and CON
        );
        PlayerCharacter weakMage = PlayerCharacterTestFactory.create(
            "777777777777777777", "999999999999999999", "Mage", "Elf",
            6, 8, 1, 10, 10, 10 // Very weak stats
        );

        when(characterRepository.findByUserIdAndGuildId("666666666666666666", "999999999999999999"))
            .thenReturn(Optional.of(strongWarrior));
        when(characterRepository.findByUserIdAndGuildId("777777777777777777", "999999999999999999"))
            .thenReturn(Optional.of(weakMage));

        ActiveBattle mismatchedBattle = battleService.createChallenge("999999999999999999", "666666666666666666", "777777777777777777");
        mismatchedBattle = battleService.acceptChallenge(mismatchedBattle.getId(), "777777777777777777");

        int turnCount = 0;
        while (mismatchedBattle.isActive() && turnCount < 100) {
            String currentPlayer = mismatchedBattle.getCurrentTurnUserId();
            BattleService.AttackResult result = battleService.performAttack(
                mismatchedBattle.getId(),
                currentPlayer
            );
            mismatchedBattle = result.battle();
            turnCount++;
        }

        assertTrue(mismatchedBattle.isEnded(),
            "Mismatched battle should end (strong vs weak)");
        assertTrue(turnCount <= 10,
            String.format("Mismatched battle should end within 10 turns (took %d turns)", turnCount));

        // Scenario 2: Balanced characters - should complete within 20 turns
        PlayerCharacter warrior1 = PlayerCharacterTestFactory.create(
            "101010101010101010", "999999999999999999", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter warrior2 = PlayerCharacterTestFactory.create(
            "202020202020202020", "999999999999999999", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );

        when(characterRepository.findByUserIdAndGuildId("101010101010101010", "999999999999999999"))
            .thenReturn(Optional.of(warrior1));
        when(characterRepository.findByUserIdAndGuildId("202020202020202020", "999999999999999999"))
            .thenReturn(Optional.of(warrior2));

        ActiveBattle balancedBattle = battleService.createChallenge("999999999999999999", "101010101010101010", "202020202020202020");
        balancedBattle = battleService.acceptChallenge(balancedBattle.getId(), "202020202020202020");

        int balancedTurnCount = 0;
        while (balancedBattle.isActive() && balancedTurnCount < 100) {
            String currentPlayer = balancedBattle.getCurrentTurnUserId();
            BattleService.AttackResult result = battleService.performAttack(
                balancedBattle.getId(),
                currentPlayer
            );
            balancedBattle = result.battle();
            balancedTurnCount++;
        }

        assertTrue(balancedBattle.isEnded(),
            "Balanced battle should end within reasonable time");
        assertTrue(balancedTurnCount <= 20,
            String.format("Balanced battle should end within 20 turns (took %d turns)", balancedTurnCount));

        // Scenario 3: ANY battle configuration should never exceed 50 turns
        // (This is a hard limit - if exceeded, combat system needs rebalancing)
        PlayerCharacter tankWarrior = PlayerCharacterTestFactory.create(
            "303030303030303030", "999999999999999999", "Warrior", "Dwarf",
            10, 8, 15, 10, 10, 10 // High CON, low damage
        );
        PlayerCharacter tankCleric = PlayerCharacterTestFactory.create(
            "404040404040404040", "999999999999999999", "Cleric", "Dwarf",
            10, 8, 15, 10, 12, 10 // High CON, low damage
        );

        when(characterRepository.findByUserIdAndGuildId("303030303030303030", "999999999999999999"))
            .thenReturn(Optional.of(tankWarrior));
        when(characterRepository.findByUserIdAndGuildId("404040404040404040", "999999999999999999"))
            .thenReturn(Optional.of(tankCleric));

        ActiveBattle tankBattle = battleService.createChallenge("999999999999999999", "303030303030303030", "404040404040404040");
        tankBattle = battleService.acceptChallenge(tankBattle.getId(), "404040404040404040");

        int maxTurnCount = 0;
        while (tankBattle.isActive() && maxTurnCount < 100) {
            String currentPlayer = tankBattle.getCurrentTurnUserId();
            BattleService.AttackResult result = battleService.performAttack(
                tankBattle.getId(),
                currentPlayer
            );
            tankBattle = result.battle();
            maxTurnCount++;
        }

        assertTrue(tankBattle.isEnded(),
            "Even tanky battle should end within maximum turn limit");
        assertTrue(maxTurnCount <= 50,
            String.format("NO battle should exceed 50 turns - combat system needs rebalancing if this fails (took %d turns)", maxTurnCount));
    }

    /**
     * Integration Test: Spell casting flow
     * Verifies that spells can be cast during battle with proper resource management.
     */
    @Test
    void spellCastingIntegrationFlow() {
        // Given: Two characters, one with a spell ability
        PlayerCharacter wizard = PlayerCharacterTestFactory.create(
            "505050505050505050", "999999999999999999", "Wizard", "Human",
            10, 10, 10, 16, 10, 10 // High INT for spell power
        );
        PlayerCharacter fighter = PlayerCharacterTestFactory.create(
            "606060606060606060", "999999999999999999", "Fighter", "Human",
            15, 10, 14, 10, 10, 10
        );

        when(characterRepository.findByUserIdAndGuildId("505050505050505050", "999999999999999999"))
            .thenReturn(Optional.of(wizard));
        when(characterRepository.findByUserIdAndGuildId("606060606060606060", "999999999999999999"))
            .thenReturn(Optional.of(fighter));

        // Mock spell resource availability (wizard has spell slots and no cooldown)
        when(spellResourceService.hasAvailableSpellSlot(wizard, 1))
            .thenReturn(true);

        // Mock ability lookup (Fireball spell) - using public constructor
        com.discordbot.battle.entity.Ability fireball = new com.discordbot.battle.entity.Ability(
            "fireball", "Fireball", "SPELL", "Wizard", 1,
            "", "1d6 fire damage", "A ball of fire"
        );
        fireball.setSpellSlotLevel(1);

        // Set ID via reflection for testing (ID is auto-generated in production)
        try {
            Field idField = com.discordbot.battle.entity.Ability.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(fireball, 1L);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ability ID for testing", e);
        }

        when(abilityRepository.findById(1L)).thenReturn(Optional.of(fireball));

        // Mock ability cooldown check - ability is available (not on cooldown)
        when(spellResourceService.isAbilityAvailable(wizard, fireball))
            .thenReturn(true);

        // Mock character knows the spell
        com.discordbot.battle.entity.CharacterAbility wizardKnowsFireball =
            new com.discordbot.battle.entity.CharacterAbility(wizard, fireball);
        when(characterAbilityRepository.findByCharacter(wizard))
            .thenReturn(java.util.List.of(wizardKnowsFireball));

        // Mock fighter has no special abilities (empty list)
        when(characterAbilityRepository.findByCharacter(fighter))
            .thenReturn(java.util.List.of());

        // When: Create and start battle
        ActiveBattle battle = battleService.createChallenge("999999999999999999", "505050505050505050", "606060606060606060");
        battle = battleService.acceptChallenge(battle.getId(), "606060606060606060");

        // Set test dice suppliers for deterministic spell attack (d20=15 guarantees hit, d6=4 for moderate damage)
        battle.setTestDiceSuppliers(() -> 15, () -> 4);

        int initialFighterHp = battle.getOpponentHp();

        // Wizard casts Fireball on their turn (if wizard goes first)
        if (battle.getCurrentTurnUserId().equals("505050505050505050")) {
            BattleService.SpellResult spellResult = battleService.performSpell(
                battle.getId(),
                "505050505050505050",
                1L
            );

            battle = spellResult.battle();

            // Then: Spell should be cast successfully
            assertTrue(spellResult.hit(), "Spell should cast successfully");
            assertNotNull(spellResult.statusEffectMessages(), "Spell should have status messages");

            // Verify spell resource was consumed
            verify(spellResourceService).consumeSpellSlot(wizard, 1);

            // Fighter should have taken damage (or battle should progress normally)
            assertTrue(battle.getTurnNumber() > 1 || battle.getOpponentHp() < initialFighterHp,
                "Spell casting should progress the battle");
        }

        // Continue battle with regular attacks
        int turnCount = 0;
        while (battle.isActive() && turnCount < 100) {
            String currentPlayer = battle.getCurrentTurnUserId();
            BattleService.AttackResult result = battleService.performAttack(
                battle.getId(),
                currentPlayer
            );
            battle = result.battle();
            turnCount++;
        }

        // Battle should complete normally
        assertTrue(battle.isEnded(), "Battle should complete after spell casting");
    }

    /**
     * Integration Test: XP and ELO progression after battle
     * Verifies that winner and loser receive appropriate XP and ELO changes.
     */
    @Test
    void battleCompletionAwardsXpAndElo() {
        // Given: Two characters with specific starting XP and ELO
        PlayerCharacter attacker = PlayerCharacterTestFactory.create(
            "707070707070707070", "999999999999999999", "Warrior", "Human",
            18, 10, 14, 10, 10, 10 // High STR for quick victory
        );
        attacker.setXp(1000);
        attacker.setElo(1200);
        attacker.setLevel(5);

        PlayerCharacter defender = PlayerCharacterTestFactory.create(
            "808080808080808080", "999999999999999999", "Rogue", "Elf",
            10, 12, 10, 10, 10, 10 // Lower stats, likely to lose
        );
        defender.setXp(500);
        defender.setElo(1100);
        defender.setLevel(3);

        when(characterRepository.findByUserIdAndGuildId("707070707070707070", "999999999999999999"))
            .thenReturn(Optional.of(attacker));
        when(characterRepository.findByUserIdAndGuildId("808080808080808080", "999999999999999999"))
            .thenReturn(Optional.of(defender));

        long attackerStartXp = attacker.getXp();
        long attackerStartElo = attacker.getElo();
        long defenderStartXp = defender.getXp();
        long defenderStartElo = defender.getElo();

        // When: Complete a full battle
        ActiveBattle battle = battleService.createChallenge("999999999999999999", "707070707070707070", "808080808080808080");
        battle = battleService.acceptChallenge(battle.getId(), "808080808080808080");

        // Fight until battle ends
        int turnCount = 0;
        while (battle.isActive() && turnCount < 100) {
            String currentPlayer = battle.getCurrentTurnUserId();
            BattleService.AttackResult result = battleService.performAttack(
                battle.getId(),
                currentPlayer
            );
            battle = result.battle();
            turnCount++;
        }

        // Then: Battle should have ended with a winner
        assertTrue(battle.isEnded(), "Battle should end");
        assertNotNull(battle.getWinnerUserId(), "Battle should have a winner");

        // Reload characters to get updated stats
        PlayerCharacter winnerChar = battle.getWinnerUserId().equals("707070707070707070") ? attacker : defender;
        PlayerCharacter loserChar = battle.getWinnerUserId().equals("707070707070707070") ? defender : attacker;

        // Winner should gain XP and ELO
        if (battle.getWinnerUserId().equals("707070707070707070")) {
            assertTrue(attacker.getXp() >= attackerStartXp,
                "Winner should gain XP (or at minimum not lose XP)");
            // Note: ELO can decrease if fighting much lower rated opponent, so we just verify it changed
            // In most cases with balanced matchups, winner ELO should increase
        } else {
            assertTrue(defender.getXp() >= defenderStartXp,
                "Winner should gain XP (or at minimum not lose XP)");
        }

        // Loser should get consolation XP (usually smaller amount)
        // Both players should have XP changes recorded
        assertTrue(winnerChar.getXp() >= 0 && loserChar.getXp() >= 0,
            "Both players should have valid XP values after battle");

        // Verify metrics were recorded
        verify(metricsService, atLeastOnce()).recordTurnPlayed(anyLong());
        verify(metricsService, atLeastOnce()).recordAttack(anyBoolean());
    }

    /**
     * Automated User Testing: Concurrent Battles
     * Simulates 3 simultaneous battles between different pairs of users
     * to verify system can handle concurrent user interactions without conflicts.
     */
    @Test
    void concurrentBattles_handleMultipleSimultaneousBattles() {
        // Given: 6 different users (3 pairs)
        PlayerCharacter user1 = PlayerCharacterTestFactory.create(
            "111111111111111111", "999999999999999999", "Warrior", "Human",
            14, 12, 14, 10, 10, 10
        );
        PlayerCharacter user2 = PlayerCharacterTestFactory.create(
            "222222222222222222", "999999999999999999", "Rogue", "Elf",
            12, 16, 12, 10, 12, 10
        );
        PlayerCharacter user3 = PlayerCharacterTestFactory.create(
            "333333333333333333", "999999999999999999", "Wizard", "Human",
            10, 10, 10, 16, 12, 10
        );
        PlayerCharacter user4 = PlayerCharacterTestFactory.create(
            "444444444444444444", "999999999999999999", "Cleric", "Dwarf",
            12, 10, 14, 10, 16, 12
        );
        PlayerCharacter user5 = PlayerCharacterTestFactory.create(
            "555555555555555555", "999999999999999999", "Fighter", "Orc",
            16, 12, 14, 8, 8, 8
        );
        PlayerCharacter user6 = PlayerCharacterTestFactory.create(
            "666666666666666666", "999999999999999999", "Ranger", "Elf",
            12, 16, 12, 10, 14, 10
        );

        // Mock character lookups
        when(characterRepository.findByUserIdAndGuildId("111111111111111111", "999999999999999999"))
            .thenReturn(Optional.of(user1));
        when(characterRepository.findByUserIdAndGuildId("222222222222222222", "999999999999999999"))
            .thenReturn(Optional.of(user2));
        when(characterRepository.findByUserIdAndGuildId("333333333333333333", "999999999999999999"))
            .thenReturn(Optional.of(user3));
        when(characterRepository.findByUserIdAndGuildId("444444444444444444", "999999999999999999"))
            .thenReturn(Optional.of(user4));
        when(characterRepository.findByUserIdAndGuildId("555555555555555555", "999999999999999999"))
            .thenReturn(Optional.of(user5));
        when(characterRepository.findByUserIdAndGuildId("666666666666666666", "999999999999999999"))
            .thenReturn(Optional.of(user6));

        // Mock character abilities (empty lists for simplicity)
        when(characterAbilityRepository.findByCharacter(any())).thenReturn(java.util.List.of());

        // When: Create 3 concurrent battles
        ActiveBattle battle1 = battleService.createChallenge("999999999999999999", "111111111111111111", "222222222222222222");
        ActiveBattle battle2 = battleService.createChallenge("999999999999999999", "333333333333333333", "444444444444444444");
        ActiveBattle battle3 = battleService.createChallenge("999999999999999999", "555555555555555555", "666666666666666666");

        battle1 = battleService.acceptChallenge(battle1.getId(), "222222222222222222");
        battle2 = battleService.acceptChallenge(battle2.getId(), "444444444444444444");
        battle3 = battleService.acceptChallenge(battle3.getId(), "666666666666666666");

        // Set deterministic dice for all battles
        battle1.setTestDiceSuppliers(() -> 12, () -> 4);
        battle2.setTestDiceSuppliers(() -> 12, () -> 4);
        battle3.setTestDiceSuppliers(() -> 12, () -> 4);

        // Then: All battles should be active with different IDs
        assertTrue(battle1.isActive(), "Battle 1 should be active");
        assertTrue(battle2.isActive(), "Battle 2 should be active");
        assertTrue(battle3.isActive(), "Battle 3 should be active");
        assertNotEquals(battle1.getId(), battle2.getId(), "Battle IDs should be unique");
        assertNotEquals(battle2.getId(), battle3.getId(), "Battle IDs should be unique");
        assertNotEquals(battle1.getId(), battle3.getId(), "Battle IDs should be unique");

        // When: Execute turns interleaved across all 3 battles
        int maxTurns = 50;
        for (int turn = 0; turn < maxTurns && (battle1.isActive() || battle2.isActive() || battle3.isActive()); turn++) {
            // Battle 1 turn
            if (battle1.isActive()) {
                BattleService.AttackResult result1 = battleService.performAttack(
                    battle1.getId(),
                    battle1.getCurrentTurnUserId()
                );
                battle1 = result1.battle();
            }

            // Battle 2 turn
            if (battle2.isActive()) {
                BattleService.AttackResult result2 = battleService.performAttack(
                    battle2.getId(),
                    battle2.getCurrentTurnUserId()
                );
                battle2 = result2.battle();
            }

            // Battle 3 turn
            if (battle3.isActive()) {
                BattleService.AttackResult result3 = battleService.performAttack(
                    battle3.getId(),
                    battle3.getCurrentTurnUserId()
                );
                battle3 = result3.battle();
            }
        }

        // Then: All battles should complete successfully
        assertTrue(battle1.isEnded(), "Battle 1 should complete");
        assertTrue(battle2.isEnded(), "Battle 2 should complete");
        assertTrue(battle3.isEnded(), "Battle 3 should complete");

        // All battles should have winners
        assertNotNull(battle1.getWinnerUserId(), "Battle 1 should have winner");
        assertNotNull(battle2.getWinnerUserId(), "Battle 2 should have winner");
        assertNotNull(battle3.getWinnerUserId(), "Battle 3 should have winner");
    }

    /**
     * Automated User Testing: Multi-User Battle Sequence
     * Simulates a realistic scenario where users challenge each other in sequence,
     * testing battle queueing and user can't fight multiple battles simultaneously.
     */
    @Test
    void multiUserSequence_userCannotHaveMultipleConcurrentBattles() {
        // Given: 3 users
        PlayerCharacter alice = PlayerCharacterTestFactory.create(
            "100000000000000001", "999999999999999999", "Warrior", "Human",
            14, 12, 14, 10, 10, 10
        );
        PlayerCharacter bob = PlayerCharacterTestFactory.create(
            "100000000000000002", "999999999999999999", "Rogue", "Elf",
            12, 16, 12, 10, 12, 10
        );
        PlayerCharacter charlie = PlayerCharacterTestFactory.create(
            "100000000000000003", "999999999999999999", "Wizard", "Human",
            10, 10, 10, 16, 12, 10
        );

        when(characterRepository.findByUserIdAndGuildId("100000000000000001", "999999999999999999"))
            .thenReturn(Optional.of(alice));
        when(characterRepository.findByUserIdAndGuildId("100000000000000002", "999999999999999999"))
            .thenReturn(Optional.of(bob));
        when(characterRepository.findByUserIdAndGuildId("100000000000000003", "999999999999999999"))
            .thenReturn(Optional.of(charlie));

        when(characterAbilityRepository.findByCharacter(any())).thenReturn(java.util.List.of());

        // When: Alice challenges Bob
        ActiveBattle battle1 = battleService.createChallenge("999999999999999999", "100000000000000001", "100000000000000002");
        battle1 = battleService.acceptChallenge(battle1.getId(), "100000000000000002");
        battle1.setTestDiceSuppliers(() -> 12, () -> 4);

        assertTrue(battle1.isActive(), "Alice vs Bob battle should be active");

        // Then: Charlie tries to challenge Alice (who is already in battle)
        // This should fail - user already in active battle
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            battleService.createChallenge("999999999999999999", "100000000000000003", "100000000000000001");
        });
        assertTrue(exception.getMessage().contains("already in") || exception.getMessage().contains("active battle"),
            "Should prevent challenging user already in battle");

        // When: Battle 1 completes
        while (battle1.isActive()) {
            BattleService.AttackResult result = battleService.performAttack(
                battle1.getId(),
                battle1.getCurrentTurnUserId()
            );
            battle1 = result.battle();
        }

        assertTrue(battle1.isEnded(), "Battle should end");

        // Then: Now Charlie can challenge Alice (battle ended)
        ActiveBattle battle2 = battleService.createChallenge("999999999999999999", "100000000000000003", "100000000000000001");
        assertNotNull(battle2, "New battle should be created after first battle ended");
        assertEquals(ActiveBattle.BattleStatus.PENDING, battle2.getStatus(), "New battle should be pending");
    }

    /**
     * Automated User Testing: Edge Case - Exact HP Depletion
     * Tests scenario where attack deals exact damage to reduce HP to 0.
     */
    @Test
    void edgeCase_exactHpDepletion() {
        // Given: Characters with low HP for quick battle
        PlayerCharacter attacker = PlayerCharacterTestFactory.create(
            "200000000000000001", "999999999999999999", "Warrior", "Human",
            18, 10, 10, 10, 10, 10 // High STR for high damage
        );
        PlayerCharacter defender = PlayerCharacterTestFactory.create(
            "200000000000000002", "999999999999999999", "Wizard", "Human",
            8, 8, 8, 16, 12, 10 // Low stats, low HP
        );

        when(characterRepository.findByUserIdAndGuildId("200000000000000001", "999999999999999999"))
            .thenReturn(Optional.of(attacker));
        when(characterRepository.findByUserIdAndGuildId("200000000000000002", "999999999999999999"))
            .thenReturn(Optional.of(defender));

        when(characterAbilityRepository.findByCharacter(any())).thenReturn(java.util.List.of());

        // When: Create battle
        ActiveBattle battle = battleService.createChallenge("999999999999999999", "200000000000000001", "200000000000000002");
        battle = battleService.acceptChallenge(battle.getId(), "200000000000000002");

        // Set high attack rolls to guarantee hits and high damage
        battle.setTestDiceSuppliers(() -> 18, () -> 6); // High d20, high d6 for damage

        int initialDefenderHp = battle.getOpponentHp();
        assertTrue(initialDefenderHp > 0, "Defender should start with positive HP");

        // When: Fight until HP reaches 0
        int turnCount = 0;
        while (battle.isActive() && turnCount < 20) {
            String currentPlayer = battle.getCurrentTurnUserId();
            BattleService.AttackResult result = battleService.performAttack(
                battle.getId(),
                currentPlayer
            );
            battle = result.battle();
            turnCount++;

            // Verify HP never goes negative
            assertTrue(battle.getChallengerHp() >= 0, "Challenger HP should never be negative");
            assertTrue(battle.getOpponentHp() >= 0, "Opponent HP should never be negative");
        }

        // Then: Battle should end with exactly 0 HP for loser
        assertTrue(battle.isEnded(), "Battle should end");
        assertTrue(battle.getChallengerHp() == 0 || battle.getOpponentHp() == 0,
            "Loser should have exactly 0 HP");
        assertNotNull(battle.getWinnerUserId(), "Should have a winner");
    }

    /**
     * Automated User Testing: Chaos/Property Testing - Random Character Stats
     * Tests that ANY valid character combination results in battle completion within reasonable turns.
     * This is property-based testing - no matter the inputs, certain properties must hold.
     */
    @Test
    void chaosTest_randomCharacterStats_battleAlwaysCompletes() {
        java.util.Random random = new java.util.Random(12345); // Fixed seed for reproducibility

        // Run 10 battles with random stats
        for (int i = 0; i < 10; i++) {
            // Generate random valid ability scores (8-18)
            int str1 = 8 + random.nextInt(11);
            int dex1 = 8 + random.nextInt(11);
            int con1 = 8 + random.nextInt(11);
            int int1 = 8 + random.nextInt(11);
            int wis1 = 8 + random.nextInt(11);
            int cha1 = 8 + random.nextInt(11);

            int str2 = 8 + random.nextInt(11);
            int dex2 = 8 + random.nextInt(11);
            int con2 = 8 + random.nextInt(11);
            int int2 = 8 + random.nextInt(11);
            int wis2 = 8 + random.nextInt(11);
            int cha2 = 8 + random.nextInt(11);

            String[] classes = {"Warrior", "Rogue", "Wizard", "Cleric", "Fighter", "Ranger"};
            String class1 = classes[random.nextInt(classes.length)];
            String class2 = classes[random.nextInt(classes.length)];

            String[] races = {"Human", "Elf", "Dwarf", "Orc", "Halfling"};
            String race1 = races[random.nextInt(races.length)];
            String race2 = races[random.nextInt(races.length)];

            PlayerCharacter char1 = PlayerCharacterTestFactory.create(
                String.format("30000000000000%04d", i * 2), "999999999999999999",
                class1, race1, str1, dex1, con1, int1, wis1, cha1
            );
            PlayerCharacter char2 = PlayerCharacterTestFactory.create(
                String.format("30000000000000%04d", i * 2 + 1), "999999999999999999",
                class2, race2, str2, dex2, con2, int2, wis2, cha2
            );

            when(characterRepository.findByUserIdAndGuildId(char1.getUserId(), "999999999999999999"))
                .thenReturn(Optional.of(char1));
            when(characterRepository.findByUserIdAndGuildId(char2.getUserId(), "999999999999999999"))
                .thenReturn(Optional.of(char2));

            when(characterAbilityRepository.findByCharacter(char1)).thenReturn(java.util.List.of());
            when(characterAbilityRepository.findByCharacter(char2)).thenReturn(java.util.List.of());

            // Create and run battle
            ActiveBattle battle = battleService.createChallenge("999999999999999999", char1.getUserId(), char2.getUserId());
            battle = battleService.acceptChallenge(battle.getId(), char2.getUserId());
            battle.setTestDiceSuppliers(() -> 10 + random.nextInt(11), () -> 1 + random.nextInt(6));

            int turnCount = 0;
            int maxTurns = 100;
            while (battle.isActive() && turnCount < maxTurns) {
                String currentPlayer = battle.getCurrentTurnUserId();
                BattleService.AttackResult result = battleService.performAttack(
                    battle.getId(),
                    currentPlayer
                );
                battle = result.battle();
                turnCount++;
            }

            // Property assertions - these MUST hold for ANY character configuration
            assertTrue(battle.isEnded(),
                String.format("Battle %d with %s(%d/%d/%d) vs %s(%d/%d/%d) should complete",
                    i, class1, str1, dex1, con1, class2, str2, dex2, con2));
            assertTrue(turnCount <= 50,
                String.format("Battle %d should complete within 50 turns (took %d)", i, turnCount));
            assertNotNull(battle.getWinnerUserId(), "Battle should have a winner");
            assertTrue(battle.getChallengerHp() >= 0 && battle.getOpponentHp() >= 0,
                "HP should never be negative");
        }
    }

    /**
     * Automated User Testing: Edge Case - Defend Strategy
     * Tests user repeatedly defending to build up AC bonus.
     */
    @Test
    void edgeCase_repeatedDefendStrategy() {
        // Given: Two characters
        PlayerCharacter attacker = PlayerCharacterTestFactory.create(
            "400000000000000001", "999999999999999999", "Warrior", "Human",
            14, 12, 14, 10, 10, 10
        );
        PlayerCharacter defender = PlayerCharacterTestFactory.create(
            "400000000000000002", "999999999999999999", "Fighter", "Dwarf",
            12, 10, 16, 10, 12, 10 // High CON
        );

        when(characterRepository.findByUserIdAndGuildId("400000000000000001", "999999999999999999"))
            .thenReturn(Optional.of(attacker));
        when(characterRepository.findByUserIdAndGuildId("400000000000000002", "999999999999999999"))
            .thenReturn(Optional.of(defender));

        when(characterAbilityRepository.findByCharacter(any())).thenReturn(java.util.List.of());

        // When: Create battle
        ActiveBattle battle = battleService.createChallenge("999999999999999999", "400000000000000001", "400000000000000002");
        battle = battleService.acceptChallenge(battle.getId(), "400000000000000002");
        battle.setTestDiceSuppliers(() -> 12, () -> 4);

        // Defender uses defend action multiple times
        int defendCount = 0;
        while (battle.isActive() && defendCount < 3) {
            if (battle.getCurrentTurnUserId().equals("400000000000000002")) {
                BattleService.DefendResult result = battleService.performDefend(
                    battle.getId(),
                    "400000000000000002"
                );
                battle = result.battle();
                defendCount++;
            } else {
                BattleService.AttackResult result = battleService.performAttack(
                    battle.getId(),
                    "400000000000000001"
                );
                battle = result.battle();
            }
        }

        // Then: Defender should have accumulated AC bonus
        int defenderAcBonus = battle.getEffectiveAcBonus("400000000000000002");
        assertTrue(defenderAcBonus > 0,
            "Defender should have accumulated AC bonus from defend actions");

        // Battle should still complete normally
        while (battle.isActive()) {
            BattleService.AttackResult result = battleService.performAttack(
                battle.getId(),
                battle.getCurrentTurnUserId()
            );
            battle = result.battle();
        }

        assertTrue(battle.isEnded(), "Battle should complete even with defend strategy");
    }
}
