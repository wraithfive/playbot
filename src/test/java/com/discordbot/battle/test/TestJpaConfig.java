package com.discordbot.battle.test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Minimal JPA configuration for repository tests.
 * Prevents loading the full Bot application context.
 * Only active when "repository-test" profile is enabled.
 * Uses in-memory H2 database to avoid file locking issues.
 */
@TestConfiguration
@Profile("repository-test")
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.discordbot.battle.repository")
@EntityScan(basePackages = "com.discordbot.battle.entity")
@ComponentScan(
    basePackages = "com.discordbot.battle",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.discordbot\\.battle\\.controller\\..*"
    )
)
public class TestJpaConfig {
    
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
