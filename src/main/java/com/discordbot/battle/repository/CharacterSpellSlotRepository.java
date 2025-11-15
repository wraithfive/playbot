package com.discordbot.battle.repository;

import com.discordbot.battle.entity.CharacterSpellSlot;
import com.discordbot.battle.entity.PlayerCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterSpellSlotRepository extends JpaRepository<CharacterSpellSlot, Long> {
    
    /**
     * Find all spell slots for a character.
     */
    List<CharacterSpellSlot> findByCharacterOrderBySlotLevel(PlayerCharacter character);
    
    /**
     * Find a specific spell slot level for a character.
     */
    Optional<CharacterSpellSlot> findByCharacterAndSlotLevel(PlayerCharacter character, int slotLevel);
    
    /**
     * Delete all spell slots for a character (useful for character deletion or respec).
     */
    @Transactional
    void deleteByCharacter(PlayerCharacter character);
}
