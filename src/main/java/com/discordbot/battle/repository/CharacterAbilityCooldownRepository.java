package com.discordbot.battle.repository;

import com.discordbot.battle.entity.Ability;
import com.discordbot.battle.entity.CharacterAbilityCooldown;
import com.discordbot.battle.entity.PlayerCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterAbilityCooldownRepository extends JpaRepository<CharacterAbilityCooldown, Long> {
    
    /**
     * Find cooldown for a specific character and ability.
     */
    Optional<CharacterAbilityCooldown> findByCharacterAndAbility(PlayerCharacter character, Ability ability);
    
    /**
     * Find all cooldowns for a character.
     */
    List<CharacterAbilityCooldown> findByCharacter(PlayerCharacter character);
    
    /**
     * Delete cooldown tracking for a character and ability (useful for cleanup).
     */
    @Transactional
    void deleteByCharacterAndAbility(PlayerCharacter character, Ability ability);
    
    /**
     * Delete all cooldowns for a character (useful for character deletion).
     */
    @Transactional
    void deleteByCharacter(PlayerCharacter character);
    
    /**
     * Find all expired cooldowns (for cleanup jobs).
     */
    List<CharacterAbilityCooldown> findByAvailableAtBefore(LocalDateTime timestamp);
}
