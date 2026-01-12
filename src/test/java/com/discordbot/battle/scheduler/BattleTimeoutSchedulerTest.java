package com.discordbot.battle.scheduler;

import com.discordbot.battle.config.BattleProperties;
import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.service.BattleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BattleTimeoutSchedulerTest {

    private BattleService battleService;
    private BattleProperties battleProperties;
    private BattleTimeoutScheduler scheduler;

    private static final String GUILD = "g1";
    private static final String USER_A = "userA";
    private static final String USER_B = "userB";

    @BeforeEach
    void setup() {
        battleService = mock(BattleService.class);
        battleProperties = new BattleProperties();
        battleProperties.setEnabled(true);
        scheduler = new BattleTimeoutScheduler(battleService, battleProperties);
    }

    @Test
    void checkAndTimeoutStaleBattles_skipsWhenBattleSystemDisabled() {
        battleProperties.setEnabled(false);

        scheduler.checkAndTimeoutStaleBattles();

        verify(battleService, never()).getAllActiveBattles();
        verify(battleService, never()).checkTurnTimeout(any());
        verify(battleService, never()).timeoutTurn(any());
    }

    @Test
    void checkAndTimeoutStaleBattles_processesNoBattlesWhenNoneActive() {
        when(battleService.getAllActiveBattles()).thenReturn(List.of());

        scheduler.checkAndTimeoutStaleBattles();

        verify(battleService).getAllActiveBattles();
        verify(battleService, never()).checkTurnTimeout(any());
        verify(battleService, never()).timeoutTurn(any());
    }

    @Test
    void checkAndTimeoutStaleBattles_timesOutStaleBattle() {
        ActiveBattle staleBattle = createActiveBattle("battle1");
        when(battleService.getAllActiveBattles()).thenReturn(List.of(staleBattle));
        when(battleService.checkTurnTimeout(staleBattle)).thenReturn(true);

        scheduler.checkAndTimeoutStaleBattles();

        verify(battleService).getAllActiveBattles();
        verify(battleService).checkTurnTimeout(staleBattle);
        verify(battleService).timeoutTurn(staleBattle);
    }

    @Test
    void checkAndTimeoutStaleBattles_skipsNonStaleBattles() {
        ActiveBattle freshBattle = createActiveBattle("battle1");
        when(battleService.getAllActiveBattles()).thenReturn(List.of(freshBattle));
        when(battleService.checkTurnTimeout(freshBattle)).thenReturn(false);

        scheduler.checkAndTimeoutStaleBattles();

        verify(battleService).getAllActiveBattles();
        verify(battleService).checkTurnTimeout(freshBattle);
        verify(battleService, never()).timeoutTurn(any());
    }

    @Test
    void checkAndTimeoutStaleBattles_processesMultipleStaleBattles() {
        ActiveBattle staleBattle1 = createActiveBattle("battle1");
        ActiveBattle staleBattle2 = createActiveBattle("battle2");
        ActiveBattle freshBattle = createActiveBattle("battle3");

        when(battleService.getAllActiveBattles()).thenReturn(List.of(staleBattle1, staleBattle2, freshBattle));
        when(battleService.checkTurnTimeout(staleBattle1)).thenReturn(true);
        when(battleService.checkTurnTimeout(staleBattle2)).thenReturn(true);
        when(battleService.checkTurnTimeout(freshBattle)).thenReturn(false);

        scheduler.checkAndTimeoutStaleBattles();

        verify(battleService).getAllActiveBattles();
        verify(battleService).checkTurnTimeout(staleBattle1);
        verify(battleService).checkTurnTimeout(staleBattle2);
        verify(battleService).checkTurnTimeout(freshBattle);
        verify(battleService).timeoutTurn(staleBattle1);
        verify(battleService).timeoutTurn(staleBattle2);
        verify(battleService, times(2)).timeoutTurn(any());
    }

    @Test
    void checkAndTimeoutStaleBattles_continuesOnErrorForIndividualBattle() {
        ActiveBattle staleBattle1 = createActiveBattle("battle1");
        ActiveBattle staleBattle2 = createActiveBattle("battle2");

        when(battleService.getAllActiveBattles()).thenReturn(List.of(staleBattle1, staleBattle2));
        when(battleService.checkTurnTimeout(any())).thenReturn(true);
        doThrow(new RuntimeException("Timeout failed for battle1"))
            .when(battleService).timeoutTurn(staleBattle1);

        // Should not throw - error should be caught and logged
        assertDoesNotThrow(() -> scheduler.checkAndTimeoutStaleBattles());

        // Second battle should still be processed
        verify(battleService).timeoutTurn(staleBattle1);
        verify(battleService).timeoutTurn(staleBattle2);
    }

    @Test
    void checkAndTimeoutStaleBattles_handlesServiceExceptionGracefully() {
        when(battleService.getAllActiveBattles()).thenThrow(new RuntimeException("Service error"));

        // Should not throw - error should be caught and logged
        assertDoesNotThrow(() -> scheduler.checkAndTimeoutStaleBattles());

        verify(battleService).getAllActiveBattles();
        verify(battleService, never()).checkTurnTimeout(any());
        verify(battleService, never()).timeoutTurn(any());
    }

    @Test
    void cleanupExpiredChallenges_skipsWhenBattleSystemDisabled() {
        battleProperties.setEnabled(false);

        scheduler.cleanupExpiredChallenges();

        verify(battleService, never()).cleanUpExpiredChallenges();
    }

    @Test
    void cleanupExpiredChallenges_callsServiceMethod() {
        when(battleService.cleanUpExpiredChallenges()).thenReturn(3);

        scheduler.cleanupExpiredChallenges();

        verify(battleService).cleanUpExpiredChallenges();
    }

    @Test
    void cleanupExpiredChallenges_handlesZeroExpiredChallenges() {
        when(battleService.cleanUpExpiredChallenges()).thenReturn(0);

        // Should not throw
        assertDoesNotThrow(() -> scheduler.cleanupExpiredChallenges());

        verify(battleService).cleanUpExpiredChallenges();
    }

    @Test
    void cleanupExpiredChallenges_handlesServiceException() {
        when(battleService.cleanUpExpiredChallenges()).thenThrow(new RuntimeException("Cleanup failed"));

        // Should not throw - error should be caught and logged
        assertDoesNotThrow(() -> scheduler.cleanupExpiredChallenges());

        verify(battleService).cleanUpExpiredChallenges();
    }

    private ActiveBattle createActiveBattle(String battleId) {
        // battleId parameter ignored - ActiveBattle.createPending() generates its own UUID
        ActiveBattle battle = ActiveBattle.createPending(GUILD, USER_A, USER_B);
        battle.start(50, 50);  // challengerHp, opponentHp
        return battle;
    }
}
