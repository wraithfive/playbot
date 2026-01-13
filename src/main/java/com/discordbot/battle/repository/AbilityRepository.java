package com.discordbot.battle.repository;

import com.discordbot.battle.entity.Ability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbilityRepository extends JpaRepository<Ability, Long> {
    Optional<Ability> findByKey(String key);
    List<Ability> findByType(String type);
    List<Ability> findByClassRestrictionIsNullOrClassRestriction(String classRestriction);
}
