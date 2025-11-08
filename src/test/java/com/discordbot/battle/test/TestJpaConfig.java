package com.discordbot.battle.test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal JPA configuration for repository tests.
 * Prevents loading the full Bot application context.
 * Only active when "repository-test" profile is enabled.
 */
@Configuration
@Profile("repository-test")
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.discordbot.battle.repository")
@EntityScan(basePackages = "com.discordbot.battle.entity")
public class TestJpaConfig {
    // Minimal configuration - no JDA, no CommandRouter, just JPA
}
