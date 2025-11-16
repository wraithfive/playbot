package com.discordbot.battle.service;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.BattleSession;
import com.discordbot.battle.repository.BattleSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BattleRecoveryService.
 * Tests startup recovery logic and stale battle cleanup.
 */
class BattleRecoveryServiceTest {

    @Mock
    private BattleSessionRepository sessionRepository;

    @Mock
    private BattleProperties battleProperties;

    @Mock
    private BattleProperties.CombatConfig combatConfig;

    @Mock
    private BattleProperties.CombatConfig.TurnConfig turnConfig;

    @Mock
    private ApplicationReadyEvent applicationReadyEvent;

    private BattleRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(battleProperties.isEnabled()).thenReturn(true);
        when(battleProperties.getCombat()).thenReturn(combatConfig);
        when(combatConfig.getTurn()).thenReturn(turnConfig);
        when(turnConfig.getTimeoutSeconds()).thenReturn(45);

        recoveryService = new BattleRecoveryService(sessionRepository, battleProperties);
    }

    @Test
    void recoverStaleBattlesOnStartup_abortsActiveBattles() {
        // Given: 2 stale battles from previous session
        List<BattleSession> staleBattles = List.of(
            createBattleSession("battle1", BattleSession.BattleStatus.ACTIVE),
            createBattleSession("battle2", BattleSession.BattleStatus.PENDING)
        );
        when(sessionRepository.findActiveBattles()).thenReturn(staleBattles);
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When: Recovery runs on startup
        recoveryService.recoverStaleBattlesOnStartup();

        // Then: Both battles are aborted
        ArgumentCaptor<BattleSession> battleCaptor = ArgumentCaptor.forClass(BattleSession.class);
        verify(sessionRepository, times(2)).save(battleCaptor.capture());

        List<BattleSession> savedBattles = battleCaptor.getAllValues();
        assertEquals(BattleSession.BattleStatus.ABORTED, savedBattles.get(0).getStatus());
        assertEquals(BattleSession.BattleStatus.ABORTED, savedBattles.get(1).getStatus());
        assertNotNull(savedBattles.get(0).getEndedAt());
        assertNotNull(savedBattles.get(1).getEndedAt());
    }

    @Test
    void recoverStaleBattlesOnStartup_handlesNoStaleBattles() {
        // Given: No stale battles
        when(sessionRepository.findActiveBattles()).thenReturn(List.of());

        // When: Recovery runs
        recoveryService.recoverStaleBattlesOnStartup();

        // Then: No abort operations
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void recoverStaleBattlesOnStartup_skipsWhenBattleSystemDisabled() {
        // Given: Battle system is disabled
        when(battleProperties.isEnabled()).thenReturn(false);

        // When: Recovery runs
        recoveryService.recoverStaleBattlesOnStartup();

        // Then: No database queries
        verify(sessionRepository, never()).findActiveBattles();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void recoverStaleBattlesOnStartup_continuesOnIndividualFailure() {
        // Given: 3 battles, middle one fails to abort
        BattleSession battle1 = createBattleSession("battle1", BattleSession.BattleStatus.ACTIVE);
        BattleSession battle2 = createBattleSession("battle2", BattleSession.BattleStatus.ACTIVE);
        BattleSession battle3 = createBattleSession("battle3", BattleSession.BattleStatus.ACTIVE);

        when(sessionRepository.findActiveBattles()).thenReturn(List.of(battle1, battle2, battle3));
        when(sessionRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0))  // battle1 succeeds
            .thenThrow(new RuntimeException("Database error"))  // battle2 fails
            .thenAnswer(i -> i.getArgument(0));  // battle3 succeeds

        // When: Recovery runs
        recoveryService.recoverStaleBattlesOnStartup();

        // Then: All 3 battles are attempted
        verify(sessionRepository, times(3)).save(any());
    }

    @Test
    void recoverStaleBattlesOnStartup_handlesCompleteFailure() {
        // Given: Repository throws exception
        when(sessionRepository.findActiveBattles()).thenThrow(new RuntimeException("Connection error"));

        // When: Recovery runs
        // Then: Should not throw (graceful error handling)
        assertDoesNotThrow(() -> recoveryService.recoverStaleBattlesOnStartup());
    }

    @Test
    void abortStaleBattle_setsStatusAndTimestamp() {
        // Given: Active battle
        BattleSession battle = createBattleSession("battle1", BattleSession.BattleStatus.ACTIVE);
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When: Abort stale battle
        recoveryService.abortStaleBattle(battle);

        // Then: Status is ABORTED and timestamp is set
        ArgumentCaptor<BattleSession> battleCaptor = ArgumentCaptor.forClass(BattleSession.class);
        verify(sessionRepository).save(battleCaptor.capture());

        BattleSession saved = battleCaptor.getValue();
        assertEquals(BattleSession.BattleStatus.ABORTED, saved.getStatus());
        assertNotNull(saved.getEndedAt());
    }

    @Test
    void cleanupTimedOutBattles_abortsStaleActiveBattles() {
        // Given: 2 timed-out battles (last action > 90 seconds ago, which is 2x 45s timeout)
        LocalDateTime oldTime = LocalDateTime.now().minusSeconds(100);
        List<BattleSession> staleBattles = List.of(
            createBattleSession("battle1", BattleSession.BattleStatus.ACTIVE),
            createBattleSession("battle2", BattleSession.BattleStatus.ACTIVE)
        );

        when(sessionRepository.findStaleBattles(any(LocalDateTime.class))).thenReturn(staleBattles);
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When: Cleanup runs
        int abortedCount = recoveryService.cleanupTimedOutBattles();

        // Then: Both battles are aborted
        assertEquals(2, abortedCount);
        verify(sessionRepository, times(2)).save(any());
    }

    @Test
    void cleanupTimedOutBattles_returnsZeroWhenNoBattles() {
        // Given: No stale battles
        when(sessionRepository.findStaleBattles(any(LocalDateTime.class))).thenReturn(List.of());

        // When: Cleanup runs
        int abortedCount = recoveryService.cleanupTimedOutBattles();

        // Then: Returns 0
        assertEquals(0, abortedCount);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cleanupTimedOutBattles_usesCorrectThreshold() {
        // Given: Turn timeout is 45 seconds
        when(sessionRepository.findStaleBattles(any(LocalDateTime.class))).thenReturn(List.of());

        // When: Cleanup runs
        recoveryService.cleanupTimedOutBattles();

        // Then: Uses 2x timeout (90 seconds) as threshold
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(sessionRepository).findStaleBattles(thresholdCaptor.capture());

        LocalDateTime threshold = thresholdCaptor.getValue();
        LocalDateTime expected = LocalDateTime.now().minusSeconds(90);

        // Allow 2 second tolerance for test execution time
        assertTrue(threshold.isAfter(expected.minusSeconds(2)) && threshold.isBefore(expected.plusSeconds(2)),
            "Threshold should be approximately 90 seconds ago");
    }

    @Test
    void cleanupTimedOutBattles_continuesOnIndividualFailure() {
        // Given: 3 battles, middle one fails
        BattleSession battle1 = createBattleSession("battle1", BattleSession.BattleStatus.ACTIVE);
        BattleSession battle2 = createBattleSession("battle2", BattleSession.BattleStatus.ACTIVE);
        BattleSession battle3 = createBattleSession("battle3", BattleSession.BattleStatus.ACTIVE);

        when(sessionRepository.findStaleBattles(any())).thenReturn(List.of(battle1, battle2, battle3));
        when(sessionRepository.save(any()))
            .thenAnswer(i -> i.getArgument(0))  // battle1 succeeds
            .thenThrow(new RuntimeException("Database error"))  // battle2 fails
            .thenAnswer(i -> i.getArgument(0));  // battle3 succeeds

        // When: Cleanup runs
        int abortedCount = recoveryService.cleanupTimedOutBattles();

        // Then: 2 battles aborted (failure doesn't stop processing)
        assertEquals(2, abortedCount);
        verify(sessionRepository, times(3)).save(any());
    }

    @Test
    void getAllActiveBattles_returnsActiveBattles() {
        // Given: Active battles exist
        List<BattleSession> activeBattles = List.of(
            createBattleSession("battle1", BattleSession.BattleStatus.ACTIVE),
            createBattleSession("battle2", BattleSession.BattleStatus.PENDING)
        );
        when(sessionRepository.findActiveBattles()).thenReturn(activeBattles);

        // When: Get active battles
        List<BattleSession> result = recoveryService.getAllActiveBattles();

        // Then: Returns all active battles
        assertEquals(2, result.size());
        assertEquals("battle1", result.get(0).getId());
        assertEquals("battle2", result.get(1).getId());
    }

    @Test
    void getAllActiveBattles_returnsEmptyListWhenNoBattles() {
        // Given: No active battles
        when(sessionRepository.findActiveBattles()).thenReturn(List.of());

        // When: Get active battles
        List<BattleSession> result = recoveryService.getAllActiveBattles();

        // Then: Returns empty list
        assertTrue(result.isEmpty());
    }

    @Test
    void countBattlesByStatus_returnsCorrectCount() {
        // Given: 3 completed battles
        List<BattleSession> completedBattles = List.of(
            createBattleSession("battle1", BattleSession.BattleStatus.COMPLETED),
            createBattleSession("battle2", BattleSession.BattleStatus.COMPLETED),
            createBattleSession("battle3", BattleSession.BattleStatus.COMPLETED)
        );
        when(sessionRepository.findByStatus(BattleSession.BattleStatus.COMPLETED))
            .thenReturn(completedBattles);

        // When: Count completed battles
        long count = recoveryService.countBattlesByStatus(BattleSession.BattleStatus.COMPLETED);

        // Then: Returns 3
        assertEquals(3, count);
    }

    @Test
    void countBattlesByStatus_returnsZeroForNoMatches() {
        // Given: No aborted battles
        when(sessionRepository.findByStatus(BattleSession.BattleStatus.ABORTED))
            .thenReturn(List.of());

        // When: Count aborted battles
        long count = recoveryService.countBattlesByStatus(BattleSession.BattleStatus.ABORTED);

        // Then: Returns 0
        assertEquals(0, count);
    }

    /**
     * Helper: Create a battle session for testing
     */
    private BattleSession createBattleSession(String id, BattleSession.BattleStatus status) {
        BattleSession battle = new BattleSession();
        battle.setId(id);
        battle.setGuildId("guild1");
        battle.setChallengerId("user1");
        battle.setOpponentId("user2");
        battle.setStatus(status);
        battle.setChallengerHp(50);
        battle.setOpponentHp(50);
        battle.setCurrentTurnUserId("user1");
        battle.setTurnNumber(1);
        battle.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        battle.setLastActionAt(LocalDateTime.now().minusMinutes(2));
        return battle;
    }
}
