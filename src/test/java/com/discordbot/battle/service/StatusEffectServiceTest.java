package com.discordbot.battle.service;

import com.discordbot.battle.entity.ActiveBattle;
import com.discordbot.battle.entity.BattleStatusEffect;
import com.discordbot.battle.entity.StatusEffectType;
import com.discordbot.battle.repository.BattleStatusEffectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StatusEffectServiceTest {

    private BattleStatusEffectRepository repository;
    private StatusEffectService service;

    private static final String BATTLE_ID = "battle1";
    private static final String USER_A = "userA";
    private static final String USER_B = "userB";

    @BeforeEach
    void setup() {
        repository = mock(BattleStatusEffectRepository.class);
        service = new StatusEffectService(repository);
    }

    // --- Basic Application Tests ---

    @Test
    void applyEffect_createsNewEffect_whenNotExists() {
        when(repository.findByBattleIdAndAffectedUserIdAndEffectType(BATTLE_ID, USER_A, StatusEffectType.BURN))
            .thenReturn(Optional.empty());

        ArgumentCaptor<BattleStatusEffect> captor = ArgumentCaptor.forClass(BattleStatusEffect.class);
        when(repository.save(captor.capture())).thenAnswer(i -> i.getArguments()[0]);

        BattleStatusEffect result = service.applyEffect(BATTLE_ID, USER_A, StatusEffectType.BURN,
            3, 1, 5, USER_B, "fireball", 1);

        assertNotNull(result);
        assertEquals(BATTLE_ID, result.getBattleId());
        assertEquals(USER_A, result.getAffectedUserId());
        assertEquals(StatusEffectType.BURN, result.getEffectType());
        assertEquals(3, result.getDurationTurns());
        assertEquals(1, result.getStacks());
        assertEquals(5, result.getMagnitude());
        assertEquals(USER_B, result.getSourceUserId());

        verify(repository).save(any(BattleStatusEffect.class));
    }

    @Test
    void applyEffect_addsStacks_whenStackableEffectExists() {
        BattleStatusEffect existing = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.BURN,
            2, 1, 5, USER_B, "fireball", 1);

        when(repository.findByBattleIdAndAffectedUserIdAndEffectType(BATTLE_ID, USER_A, StatusEffectType.BURN))
            .thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.applyEffect(BATTLE_ID, USER_A, StatusEffectType.BURN,
            3, 2, 5, USER_B, "fireball", 2);

        assertEquals(3, existing.getStacks()); // 1 + 2 = 3
        assertEquals(3, existing.getDurationTurns()); // Refreshed
        verify(repository).save(existing);
    }

    @Test
    void applyEffect_refreshesDuration_whenNonStackableEffectExists() {
        BattleStatusEffect existing = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.STUN,
            1, 1, 0, USER_B, "shocking-grasp", 1);

        when(repository.findByBattleIdAndAffectedUserIdAndEffectType(BATTLE_ID, USER_A, StatusEffectType.STUN))
            .thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.applyEffect(BATTLE_ID, USER_A, StatusEffectType.STUN,
            2, 1, 0, USER_B, "shocking-grasp", 2);

        assertEquals(1, existing.getStacks()); // Non-stackable, stays at 1
        assertEquals(2, existing.getDurationTurns()); // Refreshed
        verify(repository).save(existing);
    }

    // --- Turn Start Processing Tests ---

    @Test
    void processTurnStartEffects_dealsDamage_fromBurnEffect() {
        ActiveBattle battle = createTestBattle();
        int initialHp = battle.getChallengerHp();

        BattleStatusEffect burn = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.BURN,
            3, 2, 5, USER_B, "fireball", 1); // 2 stacks * 5 magnitude = 10 damage

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(burn));

        StatusEffectService.TurnStartEffectResult result = service.processTurnStartEffects(battle, USER_A);

        assertEquals(10, result.damageDealt());
        assertEquals(0, result.healingDone());
        assertFalse(result.hasStun());
        assertTrue(result.messages().contains("takes 10 damage from Burning"));
        assertEquals(initialHp - 10, battle.getChallengerHp());
    }

    @Test
    void processTurnStartEffects_healsHP_fromRegenEffect() {
        ActiveBattle battle = createTestBattle();
        battle.setChallengerHp(30); // Reduce HP so we can heal
        int initialHp = battle.getChallengerHp();

        BattleStatusEffect regen = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.REGEN,
            3, 1, 4, USER_B, "regeneration", 1); // 1 stack * 4 magnitude = 4 healing

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(regen));

        StatusEffectService.TurnStartEffectResult result = service.processTurnStartEffects(battle, USER_A);

        assertEquals(0, result.damageDealt());
        assertEquals(4, result.healingDone());
        assertFalse(result.hasStun());
        assertTrue(result.messages().contains("heals 4 HP from Regenerating"));
        assertEquals(initialHp + 4, battle.getChallengerHp());
    }

    @Test
    void processTurnStartEffects_detectsStun() {
        ActiveBattle battle = createTestBattle();

        BattleStatusEffect stun = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.STUN,
            1, 1, 0, USER_B, "shocking-grasp", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(stun));

        StatusEffectService.TurnStartEffectResult result = service.processTurnStartEffects(battle, USER_A);

        assertTrue(result.hasStun());
        assertTrue(result.messages().contains("is stunned"));
    }

    // --- Ticking Tests ---

    @Test
    void tickEffects_decrementsDuration_andRemovesExpired() {
        BattleStatusEffect effect1 = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.BURN,
            2, 1, 5, USER_B, "fireball", 1);
        BattleStatusEffect effect2 = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.POISON,
            1, 1, 3, USER_B, "poison-strike", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(effect1, effect2));

        int expiredCount = service.tickEffects(BATTLE_ID, USER_A);

        assertEquals(1, expiredCount); // effect2 expired
        assertEquals(1, effect1.getDurationTurns()); // Decremented
        assertEquals(0, effect2.getDurationTurns()); // Expired

        verify(repository).delete(effect2); // Expired effect deleted
        verify(repository).save(effect1); // Active effect saved
    }

    @Test
    void tickEffects_doesNotRemove_whenDurationStillPositive() {
        BattleStatusEffect effect = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.BURN,
            5, 1, 5, USER_B, "fireball", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(effect));

        int expiredCount = service.tickEffects(BATTLE_ID, USER_A);

        assertEquals(0, expiredCount);
        assertEquals(4, effect.getDurationTurns());
        verify(repository, never()).delete(any());
        verify(repository).save(effect);
    }

    // --- Modifier Tests ---

    @Test
    void getAcModifier_returnsBonus_fromHaste() {
        BattleStatusEffect haste = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.HASTE,
            3, 1, 2, USER_B, "haste-spell", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(haste));

        int acMod = service.getAcModifier(BATTLE_ID, USER_A);

        assertEquals(2, acMod);
    }

    @Test
    void getAcModifier_returnsPenalty_fromSlow() {
        BattleStatusEffect slow = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.SLOW,
            3, 1, 2, USER_B, "slow-spell", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(slow));

        int acMod = service.getAcModifier(BATTLE_ID, USER_A);

        assertEquals(-2, acMod);
    }

    @Test
    void getDamageModifierPercent_increaseDamage_fromStrength() {
        BattleStatusEffect strength = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.STRENGTH,
            4, 1, 20, USER_B, "bless", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(strength));

        int damageMod = service.getDamageModifierPercent(BATTLE_ID, USER_A);

        assertEquals(120, damageMod); // 100% + 20% = 120%
    }

    @Test
    void getDamageModifierPercent_decreaseDamage_fromWeakness() {
        BattleStatusEffect weakness = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.WEAKNESS,
            2, 1, 30, USER_B, "crippling-blow", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(weakness));

        int damageMod = service.getDamageModifierPercent(BATTLE_ID, USER_A);

        assertEquals(70, damageMod); // 100% - 30% = 70%
    }

    @Test
    void getIncomingDamageModifierPercent_reduceDamage_fromProtection() {
        BattleStatusEffect protection = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.PROTECTION,
            3, 1, 25, USER_B, "protection-from-evil", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(protection));

        int incomingDamageMod = service.getIncomingDamageModifierPercent(BATTLE_ID, USER_A);

        assertEquals(75, incomingDamageMod); // 100% - 25% = 75%
    }

    @Test
    void getIncomingDamageModifierPercent_increaseDamage_fromVulnerability() {
        BattleStatusEffect vulnerability = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.VULNERABILITY,
            3, 1, 30, USER_B, "sunder-armor", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(vulnerability));

        int incomingDamageMod = service.getIncomingDamageModifierPercent(BATTLE_ID, USER_A);

        assertEquals(130, incomingDamageMod); // 100% + 30% = 130%
    }

    // --- Shield Tests ---

    @Test
    void getShieldValue_returnsShieldHP() {
        BattleStatusEffect shield = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.SHIELD,
            3, 1, 15, USER_B, "shield-of-faith", 1);

        when(repository.findByBattleIdAndAffectedUserIdAndEffectType(BATTLE_ID, USER_A, StatusEffectType.SHIELD))
            .thenReturn(Optional.of(shield));

        int shieldValue = service.getShieldValue(BATTLE_ID, USER_A);

        assertEquals(15, shieldValue);
    }

    @Test
    void consumeShield_absorbsAllDamage_whenShieldIsSufficient() {
        BattleStatusEffect shield = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.SHIELD,
            3, 1, 15, USER_B, "shield-of-faith", 1);

        when(repository.findByBattleIdAndAffectedUserIdAndEffectType(BATTLE_ID, USER_A, StatusEffectType.SHIELD))
            .thenReturn(Optional.of(shield));
        when(repository.save(shield)).thenReturn(shield);

        int remainingDamage = service.consumeShield(BATTLE_ID, USER_A, 10);

        assertEquals(0, remainingDamage); // All damage absorbed
        assertEquals(5, shield.getMagnitude()); // 15 - 10 = 5
        verify(repository).save(shield);
        verify(repository, never()).delete(shield);
    }

    @Test
    void consumeShield_breaksShield_whenDamageExceedsShield() {
        BattleStatusEffect shield = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.SHIELD,
            3, 1, 10, USER_B, "shield-of-faith", 1);

        when(repository.findByBattleIdAndAffectedUserIdAndEffectType(BATTLE_ID, USER_A, StatusEffectType.SHIELD))
            .thenReturn(Optional.of(shield));

        int remainingDamage = service.consumeShield(BATTLE_ID, USER_A, 15);

        assertEquals(5, remainingDamage); // 15 - 10 = 5 damage gets through
        verify(repository).delete(shield); // Shield broken
    }

    @Test
    void consumeShield_deletesShield_whenFullyDepleted() {
        BattleStatusEffect shield = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.SHIELD,
            3, 1, 10, USER_B, "shield-of-faith", 1);

        when(repository.findByBattleIdAndAffectedUserIdAndEffectType(BATTLE_ID, USER_A, StatusEffectType.SHIELD))
            .thenReturn(Optional.of(shield));
        when(repository.save(shield)).thenReturn(shield);

        int remainingDamage = service.consumeShield(BATTLE_ID, USER_A, 10);

        assertEquals(0, remainingDamage);
        verify(repository).delete(shield); // Shield depleted
    }

    // --- Cleanup Tests ---

    @Test
    void cleanupBattleEffects_deletesAllEffectsForBattle() {
        service.cleanupBattleEffects(BATTLE_ID);

        verify(repository).deleteByBattleId(BATTLE_ID);
    }

    @Test
    void removeEffect_deletesSpecificEffect() {
        BattleStatusEffect effect = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.BURN,
            2, 1, 5, USER_B, "fireball", 1);

        when(repository.findByBattleIdAndAffectedUserIdAndEffectType(BATTLE_ID, USER_A, StatusEffectType.BURN))
            .thenReturn(Optional.of(effect));

        boolean removed = service.removeEffect(BATTLE_ID, USER_A, StatusEffectType.BURN);

        assertTrue(removed);
        verify(repository).delete(effect);
    }

    @Test
    void removeEffect_returnsFalse_whenEffectNotFound() {
        when(repository.findByBattleIdAndAffectedUserIdAndEffectType(BATTLE_ID, USER_A, StatusEffectType.BURN))
            .thenReturn(Optional.empty());

        boolean removed = service.removeEffect(BATTLE_ID, USER_A, StatusEffectType.BURN);

        assertFalse(removed);
        verify(repository, never()).delete(any());
    }

    @Test
    void removeAllEffects_deletesAllEffectsForUser() {
        service.removeAllEffects(BATTLE_ID, USER_A);

        verify(repository).deleteByBattleIdAndAffectedUserId(BATTLE_ID, USER_A);
    }

    // --- Display Tests ---

    @Test
    void getEffectsDisplayString_formatsEffects_correctly() {
        BattleStatusEffect burn = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.BURN,
            3, 2, 5, USER_B, "fireball", 1);
        BattleStatusEffect haste = new BattleStatusEffect(BATTLE_ID, USER_A, StatusEffectType.HASTE,
            2, 1, 2, USER_B, "haste-spell", 1);

        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of(burn, haste));

        String display = service.getEffectsDisplayString(BATTLE_ID, USER_A);

        assertTrue(display.contains("🔥 Burning (3 turns, 2 stacks)"));
        assertTrue(display.contains("⚡ Hasted (2 turns)"));
    }

    @Test
    void getEffectsDisplayString_returnsEmpty_whenNoEffects() {
        when(repository.findByBattleIdAndAffectedUserId(BATTLE_ID, USER_A))
            .thenReturn(List.of());

        String display = service.getEffectsDisplayString(BATTLE_ID, USER_A);

        assertEquals("", display);
    }

    // --- Helper Methods ---

    private ActiveBattle createTestBattle() {
        ActiveBattle battle = ActiveBattle.createPending("guild1", USER_A, USER_B);
        battle.start(50, 50);
        return battle;
    }
}
