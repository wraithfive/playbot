package com.discordbot.battle.service;

import com.discordbot.battle.entity.Ability;
import com.discordbot.battle.entity.CharacterAbility;
import com.discordbot.battle.entity.PlayerCharacter;
import com.discordbot.battle.repository.AbilityRepository;
import com.discordbot.battle.repository.CharacterAbilityRepository;
import com.discordbot.battle.repository.PlayerCharacterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core domain logic for listing and learning abilities.
 * Initial implementation is intentionally lightweight; balancing & effect parsing are future work.
 */
@Service
public class AbilityService {

    private static final Logger logger = LoggerFactory.getLogger(AbilityService.class);

    private final AbilityRepository abilityRepository;
    private final CharacterAbilityRepository characterAbilityRepository;
    private final PlayerCharacterRepository playerCharacterRepository;

    public AbilityService(AbilityRepository abilityRepository,
                          CharacterAbilityRepository characterAbilityRepository,
                          PlayerCharacterRepository playerCharacterRepository) {
        this.abilityRepository = abilityRepository;
        this.characterAbilityRepository = characterAbilityRepository;
        this.playerCharacterRepository = playerCharacterRepository;
    }

    /**
     * Returns abilities available for a character's class (includes unrestricted abilities).
     */
    @Transactional(readOnly = true)
    public List<Ability> listAvailableForCharacter(PlayerCharacter character) {
        List<Ability> all = abilityRepository.findAll();
        Set<String> learnedKeys = characterAbilityRepository.findByCharacter(character).stream()
            .map(ca -> ca.getAbility().getKey())
            .collect(Collectors.toSet());
        return all.stream()
            .filter(a -> a.getClassRestriction() == null || a.getClassRestriction().equalsIgnoreCase(character.getCharacterClass()))
            .filter(a -> !learnedKeys.contains(a.getKey()))
            .collect(Collectors.toList());
    }

    /**
     * Learn an ability if prerequisites & restrictions satisfied.
     * Returns CharacterAbility or throws IllegalStateException with human-friendly message.
     */
    @Transactional
    public CharacterAbility learnAbility(String guildId, String userId, String abilityKey) {
        PlayerCharacter character = playerCharacterRepository.findByUserIdAndGuildId(userId, guildId)
            .orElseThrow(() -> new IllegalStateException("You need a character first."));

        Ability ability = abilityRepository.findByKey(abilityKey)
            .orElseThrow(() -> new IllegalStateException("Ability not found: " + abilityKey));

        if (ability.getClassRestriction() != null && !ability.getClassRestriction().equalsIgnoreCase(character.getCharacterClass())) {
            throw new IllegalStateException("Ability restricted to class: " + ability.getClassRestriction());
        }

        // Check already learned
        boolean alreadyLearned = characterAbilityRepository.findByCharacter(character).stream()
            .anyMatch(ca -> ca.getAbility().getId().equals(ability.getId()));
        if (alreadyLearned) {
            throw new IllegalStateException("Already learned: " + ability.getName());
        }

        // Prerequisites
        List<String> prereqKeys = prereqKeys(ability);
        if (!prereqKeys.isEmpty()) {
            Set<String> learnedKeys = characterAbilityRepository.findByCharacter(character).stream()
                .map(ca -> ca.getAbility().getKey())
                .collect(Collectors.toSet());
            List<String> missing = prereqKeys.stream().filter(k -> !learnedKeys.contains(k)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Missing prerequisites: " + String.join(", ", missing));
            }
        }

        CharacterAbility link = new CharacterAbility(character, ability);
        characterAbilityRepository.save(link);
        logger.info("ABILITY_LEARN | userId={} guildId={} abilityKey={}", userId, guildId, abilityKey);
        return link;
    }

    private List<String> prereqKeys(Ability ability) {
        if (ability.getPrerequisites() == null || ability.getPrerequisites().isBlank()) return List.of();
        return Arrays.stream(ability.getPrerequisites().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
