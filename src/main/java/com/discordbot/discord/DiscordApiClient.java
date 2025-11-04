package com.discordbot.discord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Discord REST client for fetching guild roles with role colors.
 *
 * Uses the bot token from the DISCORD_TOKEN system property (set in Bot.main()).
 * Only the fields required for role color previews are modeled.
 */
@Component
public class DiscordApiClient {
    private static final Logger log = LoggerFactory.getLogger(DiscordApiClient.class);

    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String botToken;

    public DiscordApiClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.botToken = System.getProperty("DISCORD_TOKEN");
        if (this.botToken == null || this.botToken.isBlank()) {
            log.warn("DISCORD_TOKEN not set; DiscordApiClient will return empty data");
        }
    }

    /**
     * Fetch roles for a guild and return a map of roleId -> RoleColors object.
     * Never throws; returns empty map on failures.
     */
    public Map<String, RoleColors> getGuildRoleColors(String guildId) {
        if (botToken == null || botToken.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            String url = DISCORD_API_BASE + "/guilds/" + guildId + "/roles";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bot " + botToken)
                    .header("User-Agent", "Playbot (github.com/wraithfive/playbot)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                List<ApiRole> roles = mapper.readValue(resp.body(), new TypeReference<>() {});
                Map<String, RoleColors> out = new HashMap<>();
                for (ApiRole r : roles) {
                    if (r.id != null && r.colors != null) {
                        out.put(r.id, r.colors);
                    }
                }
                return out;
            } else {
                log.warn("Discord API GET /guilds/{}/roles returned status {}", guildId, resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch guild roles for {}: {}", guildId, e.toString());
        }
        return Collections.emptyMap();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ApiRole {
        @JsonProperty("id")
        public String id;

        @JsonProperty("colors")
        public RoleColors colors;
    }

    /**
     * Mirrors the Role Colors Object in Discord API docs.
     * primary_color is always present (may be 0). Others can be null.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoleColors {
        @JsonProperty("primary_color")
        public Integer primaryColor;

        @JsonProperty("secondary_color")
        public Integer secondaryColor;

        @JsonProperty("tertiary_color")
        public Integer tertiaryColor;

        public boolean isUnset() {
            return primaryColor == null || primaryColor == 0;
        }

        public boolean isGradient() {
            return secondaryColor != null;
        }

        public boolean isHolographic() {
            return tertiaryColor != null; // per docs, holographic enforced when tertiary provided
        }
    }
}
