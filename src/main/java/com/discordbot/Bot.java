package com.discordbot;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = {"com.discordbot.repository", "com.discordbot.battle.repository"})
@EntityScan(basePackages = {"com.discordbot.entity", "com.discordbot.battle.entity"})
/**
 * Main Spring Boot application entry point.
 *
 * Profile behavior:
 * - We intentionally avoid a class-level @Profile("!repository-test").
 *   Only the Discord JDA bean is gated with @Profile("!repository-test").
 *   This lets repository/JPA slice tests that use the "repository-test" profile
 *   bootstrap entities, repositories, and Liquibase without attempting to start JDA.
 */
public class Bot {

    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    public static void main(String[] args) {
        // Load .env file before Spring Boot starts
        // This ensures environment variables are available for application.properties
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        // Set environment variables as system properties so Spring can read them
        if (dotenv.get("DISCORD_TOKEN") != null) {
            System.setProperty("DISCORD_TOKEN", dotenv.get("DISCORD_TOKEN"));
        }
        if (dotenv.get("DISCORD_CLIENT_ID") != null) {
            System.setProperty("DISCORD_CLIENT_ID", dotenv.get("DISCORD_CLIENT_ID"));
        }
        if (dotenv.get("DISCORD_CLIENT_SECRET") != null) {
            System.setProperty("DISCORD_CLIENT_SECRET", dotenv.get("DISCORD_CLIENT_SECRET"));
        }
        if (dotenv.get("ADMIN_PANEL_URL") != null) {
            System.setProperty("ADMIN_PANEL_URL", dotenv.get("ADMIN_PANEL_URL"));
        }

        SpringApplication.run(Bot.class, args);
    }

    @Bean
    @Profile("!repository-test")
    public JDA jda(com.discordbot.command.CommandRouter commandRouter, 
                   com.discordbot.command.CommandRegistrar commandRegistrar,
                   com.discordbot.battle.controller.CharacterCreationInteractionHandler characterCreationInteractionHandler,
                   com.discordbot.battle.controller.BattleInteractionHandler battleInteractionHandler,
                   com.discordbot.battle.controller.AbilityInteractionHandler abilityInteractionHandler) throws InterruptedException {
        logger.info("=== Playbot Starting ===");

        // Get token from system properties (loaded in main())
        String token = System.getProperty("DISCORD_TOKEN");

        if (token == null || token.isEmpty()) {
            logger.error("DISCORD_TOKEN not found in environment variables or .env file!");
            logger.error("Please create a .env file with your bot token.");
            throw new RuntimeException("DISCORD_TOKEN not configured");
        }

        logger.info("Bot token loaded successfully");

        try {
            JDABuilder builder = JDABuilder.createDefault(token);

            // Enable necessary intents
            builder.enableIntents(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS
            );
            logger.info("Gateway intents configured: GUILD_MESSAGES, MESSAGE_CONTENT, GUILD_MEMBERS");

            // Set bot status and activity
            builder.setStatus(OnlineStatus.ONLINE);
            builder.setActivity(Activity.playing("/roll for colors | /battle-help"));
            logger.info("Bot status set to ONLINE with activity: /roll for colors | /battle-help");

            // Register event listeners
            // CommandRouter handles all slash command interactions
            // CommandRegistrar handles command registration on guild events
            // CharacterCreationInteractionHandler handles character creation button/select menu interactions
            builder.addEventListeners(commandRouter);
            builder.addEventListeners(commandRegistrar);
            builder.addEventListeners(characterCreationInteractionHandler);
            builder.addEventListeners(battleInteractionHandler);
            builder.addEventListeners(abilityInteractionHandler);
            logger.info("Event listeners registered: CommandRouter, CommandRegistrar, CharacterCreationInteractionHandler, BattleInteractionHandler, AbilityInteractionHandler");

            // Build and start the bot
            JDA jda = builder.build();
            jda.awaitReady(); // Wait for bot to be ready

            logger.info("=== Bot successfully started ===");
            logger.info("Connected as: {}", jda.getSelfUser().getName());
            logger.info("Connected to {} guilds", jda.getGuilds().size());

            return jda;

        } catch (InterruptedException e) {
            logger.error("Bot was interrupted during startup", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to start bot", e);
        } catch (Exception e) {
            logger.error("Failed to start bot", e);
            throw new RuntimeException("Failed to start bot", e);
        }
    }
}
