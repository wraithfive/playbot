package com.discordbot.battle.repository;

import com.discordbot.battle.entity.CharacterAbility;
import com.discordbot.battle.entity.PlayerCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterAbilityRepository extends JpaRepository<CharacterAbility, Long> {
    @Query("SELECT ca FROM CharacterAbility ca JOIN FETCH ca.ability WHERE ca.character = :character")
    List<CharacterAbility> findByCharacter(@Param("character") PlayerCharacter character);
    
    Optional<CharacterAbility> findByCharacterIdAndAbilityId(Long characterId, Long abilityId);
}
