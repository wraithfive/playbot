package com.discordbot.battle.effect;

import com.discordbot.battle.entity.CharacterAbility;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.util.CharacterDerivedStats;

import java.util.List;

/**
 * Calculates a character's effective combat stats by applying ability modifiers.
 * This is used during battle to compute bonuses from talents, skills, and active spells.
 */
public class CharacterStatsCalculator {

    /**
     * Base AC for unarmored characters in D&D 5e rules.
     */
    private static final int BASE_AC = 10;

    /**
     * Calculate all combat-relevant stats for a character, including ability bonuses.
     */
    public static CombatStats calculateStats(PlayerCharacter character, List<CharacterAbility> learnedAbilities, int baseHp) {
        // Parse all learned ability effects (filter nulls/blanks for defensive programming)
        List<String> effectStrings = learnedAbilities.stream()
            .filter(ca -> ca.getAbility() != null && ca.getAbility().getEffect() != null)
            .map(ca -> ca.getAbility().getEffect())
            .toList();

        AbilityEffect combinedEffect = EffectParser.parseAndCombine(effectStrings);

        // Base stats from character
        int str = character.getStrength();
        int dex = character.getDexterity();
        int con = character.getConstitution();
        int intel = character.getIntelligence();
        int wis = character.getWisdom();

        // Apply ability score modifiers (e.g., "STR+1" from Athlete talent)
        str += combinedEffect.getTotalBonus(EffectStat.STR);
        dex += combinedEffect.getTotalBonus(EffectStat.DEX);
        con += combinedEffect.getTotalBonus(EffectStat.CON);
        intel += combinedEffect.getTotalBonus(EffectStat.INT);
        wis += combinedEffect.getTotalBonus(EffectStat.WIS);
        // CHA not currently used in combat calculations but could be used for future abilities

        // Recalculate ability modifiers with bonuses applied
        int strMod = CharacterDerivedStats.abilityMod(str);
        int dexMod = CharacterDerivedStats.abilityMod(dex);
        int conMod = CharacterDerivedStats.abilityMod(con);

        // Calculate HP using D&D 5e formula: hitDieMax + CON mod + ability bonuses
        // At level 1: baseHp (hit die max) + CON mod + MAX_HP bonuses from talents/spells
        // Future leveling: will add (level-1) × (hit die average + CON mod)
        int maxHp = baseHp + conMod + combinedEffect.getTotalBonus(EffectStat.MAX_HP);
        maxHp = Math.max(1, maxHp); // Minimum 1 HP

        // Calculate AC: 10 + DEX mod + AC bonuses
        int ac = BASE_AC + dexMod + combinedEffect.getTotalBonus(EffectStat.AC);

        // Attack damage bonus: STR mod + DAMAGE bonuses
        int attackDamageBonus = strMod + combinedEffect.getTotalBonus(EffectStat.DAMAGE);

        // Spell damage bonus: casting stat modifier (INT for Mage, WIS for Cleric; otherwise best of INT/WIS)
        int castingStatScore = switch (character.getCharacterClass().toLowerCase()) {
            case "mage" -> intel;
            case "cleric" -> wis;
            default -> Math.max(intel, wis);
        };
        int spellDamageBonus = CharacterDerivedStats.abilityMod(castingStatScore)
            + combinedEffect.getTotalBonus(EffectStat.SPELL_DAMAGE);

        // Healing bonus: WIS mod + HEAL_BONUS
        int healingBonus = CharacterDerivedStats.abilityMod(wis) + combinedEffect.getTotalBonus(EffectStat.HEAL_BONUS);

        // Crit bonuses
        int critDamageBonus = combinedEffect.getTotalBonus(EffectStat.CRIT_DAMAGE);

        return new CombatStats(
            maxHp,
            ac,
            attackDamageBonus,
            spellDamageBonus,
            healingBonus,
            critDamageBonus,
            combinedEffect
        );
    }

    /**
     * Container for a character's calculated combat stats.
     */
    public record CombatStats(
        int maxHp,
        int armorClass,
        int attackDamageBonus,
        int spellDamageBonus,
        int healingBonus,
        int critDamageBonus,
        AbilityEffect combinedEffect  // Full effect for checking tags and other modifiers
    ) {}
}
