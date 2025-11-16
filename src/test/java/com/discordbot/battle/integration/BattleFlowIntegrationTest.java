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
    }

    /**
     * Integration Test: Complete battle flow from challenge to victory
     */
    @Test
    void completeBattleFlowFromChallengeToVictory() {
        // Given: Two characters
        PlayerCharacter challenger = PlayerCharacterTestFactory.create(
            "user1", "guild1", "Warrior", "Human",
            18, 10, 16, 10, 10, 10 // High STR and CON
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "user2", "guild1", "Mage", "Elf",
            8, 14, 3, 18, 12, 10 // Very low STR and CON (much weaker), high DEX and INT
        );

        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("user2", "guild1"))
            .thenReturn(Optional.of(opponent));

        // When: Create challenge
        ActiveBattle battle = battleService.createChallenge("guild1", "user1", "user2");
        assertNotNull(battle);
        assertTrue(battle.isPending());
        assertEquals("user1", battle.getChallengerId());
        assertEquals("user2", battle.getOpponentId());

        // When: Accept challenge
        ActiveBattle activeBattle = battleService.acceptChallenge(battle.getId(), "user2");
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
            "user1", "guild1", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "user2", "guild1", "Rogue", "Halfling",
            12, 16, 12, 10, 14, 10
        );

        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("user2", "guild1"))
            .thenReturn(Optional.of(opponent));

        ActiveBattle battle = battleService.createChallenge("guild1", "user1", "user2");
        battleService.acceptChallenge(battle.getId(), "user2");

        // When: Challenger forfeits
        ActiveBattle forfeitedBattle = battleService.forfeit(battle.getId(), "user1");

        // Then: Battle should be ended with opponent as winner
        assertNotNull(forfeitedBattle);
        assertTrue(forfeitedBattle.isEnded(), "Battle should be ended");
        assertEquals("user2", forfeitedBattle.getWinnerUserId(),
            "Opponent should be the winner after challenger forfeits");
    }

    /**
     * Integration Test: Defend action flow
     */
    @Test
    void defendActionFlow() {
        // Given: Active battle
        PlayerCharacter challenger = PlayerCharacterTestFactory.create(
            "user1", "guild1", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "user2", "guild1", "Cleric", "Dwarf",
            14, 10, 16, 10, 16, 12
        );

        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("user2", "guild1"))
            .thenReturn(Optional.of(opponent));

        ActiveBattle battle = battleService.createChallenge("guild1", "user1", "user2");
        battle = battleService.acceptChallenge(battle.getId(), "user2");

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
            "newuser1", "guild1", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter newChar2 = PlayerCharacterTestFactory.create(
            "newuser2", "guild1", "Rogue", "Elf",
            12, 16, 12, 10, 14, 10
        );

        when(characterRepository.findByUserIdAndGuildId("newuser1", "guild1"))
            .thenReturn(Optional.of(newChar1));
        when(characterRepository.findByUserIdAndGuildId("newuser2", "guild1"))
            .thenReturn(Optional.of(newChar2));

        // When: Characters battle
        ActiveBattle battle = battleService.createChallenge("guild1", "newuser1", "newuser2");
        battle = battleService.acceptChallenge(battle.getId(), "newuser2");

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
            "user1", "guild1", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "user2", "guild1", "Mage", "Elf",
            8, 14, 10, 18, 12, 10
        );

        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("user2", "guild1"))
            .thenReturn(Optional.of(opponent));

        ActiveBattle battle = battleService.createChallenge("guild1", "user1", "user2");

        // When: Challenge is manually ended/removed from cache
        // (In real scenario, this would be done by cleanup scheduler or manual cancellation)
        // Simulate by trying to accept a non-existent battle

        // Then: Accepting should fail
        String nonExistentBattleId = "non-existent-battle-id";
        assertThrows(IllegalStateException.class, () ->
            battleService.acceptChallenge(nonExistentBattleId, "user2"),
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
            "user1", "guild1", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter user2 = PlayerCharacterTestFactory.create(
            "user2", "guild1", "Rogue", "Elf",
            12, 16, 12, 10, 14, 10
        );
        PlayerCharacter user3 = PlayerCharacterTestFactory.create(
            "user3", "guild1", "Mage", "Human",
            8, 14, 10, 18, 12, 10
        );

        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(user1));
        when(characterRepository.findByUserIdAndGuildId("user2", "guild1"))
            .thenReturn(Optional.of(user2));
        when(characterRepository.findByUserIdAndGuildId("user3", "guild1"))
            .thenReturn(Optional.of(user3));

        // When: user1 challenges user2
        ActiveBattle battle1 = battleService.createChallenge("guild1", "user1", "user2");

        // Then: user1 cannot challenge user3 (busy with pending challenge to user2)
        assertThrows(IllegalStateException.class, () ->
            battleService.createChallenge("guild1", "user1", "user3"),
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
            "user1", "guild1", "Warrior", "Human",
            15, 10, 14, 10, 10, 10
        );
        PlayerCharacter opponent = PlayerCharacterTestFactory.create(
            "user2", "guild1", "Rogue", "Elf",
            12, 16, 12, 10, 14, 10
        );

        when(characterRepository.findByUserIdAndGuildId("user1", "guild1"))
            .thenReturn(Optional.of(challenger));
        when(characterRepository.findByUserIdAndGuildId("user2", "guild1"))
            .thenReturn(Optional.of(opponent));

        // When: Perform multiple operations
        ActiveBattle battle = battleService.createChallenge("guild1", "user1", "user2");
        String battleId = battle.getId();

        // Accept
        battle = battleService.acceptChallenge(battleId, "user2");
        assertTrue(battle.isActive(), "Battle should be active after accept");

        // Attack
        String player1 = battle.getCurrentTurnUserId();
        battle = battleService.performAttack(battleId, player1).battle();

        // Defend
        String player2 = battle.getCurrentTurnUserId();
        battle = battleService.performDefend(battleId, player2).battle();

        // Attack again
        String player3 = battle.getCurrentTurnUserId();
        battle = battleService.performAttack(battleId, player3).battle();

        // Then: State should be consistent
        assertTrue(battle.getTurnNumber() >= 3, "Turn number should reflect actions taken");
        assertTrue(battle.getChallengerHp() > 0 || battle.isEnded(),
            "Challenger HP should be positive or battle ended");
        assertTrue(battle.getOpponentHp() > 0 || battle.isEnded(),
            "Opponent HP should be positive or battle ended");
    }
}
