package com.discordbot.battle.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses effect strings from abilities into structured AbilityEffect objects.
 *
 * Effect Format Examples:
 * - "DAMAGE+3" - Add 3 to damage
 * - "DAMAGE+3,FIRE" - Add 3 damage of type FIRE
 * - "MAX_HP+5,HEAL_BONUS+2" - Add 5 max HP and 2 healing bonus
 * - "RADIANT" - Just a tag, no numeric modifier
 *
 * Grammar:
 * - Effects are comma-separated
 * - Each element is either:
 *   - STAT+VALUE or STAT-VALUE (numeric modifier)
 *   - TAG (string tag)
 */
public class EffectParser {

    // Pattern to match STAT+VALUE or STAT-VALUE
    private static final Pattern MODIFIER_PATTERN = Pattern.compile("^([A-Z_]+)([+-])(\\d+)$");

    /**
     * Parse an effect string into an AbilityEffect.
     * @param effectString The effect string from the database (e.g., "DAMAGE+3,FIRE")
     * @return Parsed AbilityEffect
     */
    public static AbilityEffect parse(String effectString) {
        if (effectString == null || effectString.isBlank()) {
            return new AbilityEffect(List.of(), List.of());
        }

        List<AbilityEffect.Modifier> modifiers = new ArrayList<>();
        List<String> tags = new ArrayList<>();

        String[] parts = effectString.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Matcher matcher = MODIFIER_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                // It's a modifier like "DAMAGE+3"
                String stat = matcher.group(1);
                String sign = matcher.group(2);
                int value = Integer.parseInt(matcher.group(3));

                // Apply sign
                if ("-".equals(sign)) {
                    value = -value;
                }

                modifiers.add(new AbilityEffect.Modifier(stat, value));
            } else {
                // It's a tag like "FIRE" or "RADIANT"
                tags.add(trimmed);
            }
        }

        return new AbilityEffect(modifiers, tags);
    }

    /**
     * Parse effects from multiple abilities and combine them.
     * Useful for calculating total character modifiers from all learned abilities.
     */
    public static AbilityEffect parseAndCombine(List<String> effectStrings) {
        List<AbilityEffect.Modifier> allModifiers = new ArrayList<>();
        List<String> allTags = new ArrayList<>();

        for (String effectString : effectStrings) {
            AbilityEffect effect = parse(effectString);
            allModifiers.addAll(effect.getModifiers());
            allTags.addAll(effect.getTags());
        }

        return new AbilityEffect(allModifiers, allTags);
    }
}
