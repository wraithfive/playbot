package com.discordbot.battle.repository;

import com.discordbot.battle.entity.Ability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@org.springframework.test.context.ActiveProfiles("repository-test")
class AbilityRepositoryTest {

    @Autowired
    private AbilityRepository abilityRepository;

    @Test
    void save_and_findByKey_works() {
        Ability a = new Ability("test-ability-key", "Test Ability", "SKILL", "Warrior", 1, "", "DAMAGE+3", "Increase damage by 3.");
        abilityRepository.save(a);

        assertThat(abilityRepository.findByKey("test-ability-key")).isPresent();
        assertThat(abilityRepository.findByKey("test-ability-key").get().getEffect()).isEqualTo("DAMAGE+3");
    }

    @Test
    void save_duplicateKey_throwsException() {
        Ability a1 = new Ability("unique-key", "First Ability", "SKILL", "Warrior", 1, "", "DAMAGE+1", "First");
        abilityRepository.save(a1);
        abilityRepository.flush();

        // Attempt to save another ability with the same key
        Ability a2 = new Ability("unique-key", "Second Ability", "TALENT", null, 1, "", "DAMAGE+2", "Second");
        
        // Expect DataIntegrityViolationException due to unique constraint on ability_key
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                abilityRepository.save(a2);
                abilityRepository.flush();
            },
            "Saving duplicate ability key should throw DataIntegrityViolationException"
        );
    }

    @Test
    void findByKey_multipleCallsConsistent() {
        // Test that findByKey returns consistent results (sanity check for indexing)
        Ability ability = new Ability("consistent-key", "Consistent Ability", "SPELL", "Mage", 1, "", "MAGIC+5", "Magic power");
        abilityRepository.save(ability);
        abilityRepository.flush();

        // Multiple lookups should return same result
        var result1 = abilityRepository.findByKey("consistent-key");
        var result2 = abilityRepository.findByKey("consistent-key");
        var result3 = abilityRepository.findByKey("consistent-key");

        assertThat(result1).isPresent();
        assertThat(result2).isPresent();
        assertThat(result3).isPresent();
        assertThat(result1.get().getKey()).isEqualTo("consistent-key");
        assertThat(result2.get().getKey()).isEqualTo("consistent-key");
        assertThat(result3.get().getKey()).isEqualTo("consistent-key");
    }
}
