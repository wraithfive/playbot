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
    // Simple in-memory cache for guild capability checks
    private final java.util.concurrent.ConcurrentHashMap<String, CacheEntry> capabilityCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CAPABILITY_TTL_MILLIS = Duration.ofMinutes(10).toMillis();

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
     * Check if a guild supports enhanced role colors (gradient/holographic).
     * Implementation: fetch guild and inspect the "features" array for known flags.
     * Never throws; returns false on failures or when token is missing.
     */
    public boolean guildSupportsEnhancedRoleColors(String guildId) {
        if (botToken == null || botToken.isBlank()) {
            return false;
        }
        // Check cache first
        try {
            CacheEntry cached = capabilityCache.get(guildId);
            long now = System.currentTimeMillis();
            if (cached != null && cached.expiresAt > now) {
                return cached.value;
            }
        } catch (Exception e) {
            // ignore cache issues to avoid impacting behavior
        }
        try {
            String url = DISCORD_API_BASE + "/guilds/" + guildId + "?with_counts=false";
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
                ApiGuild guild = mapper.readValue(resp.body(), ApiGuild.class);
                boolean supported = false;
                if (guild.features == null) {
                    supported = false;
                } else {
                // Accept several plausible/forward-compatible flags
                    for (String f : guild.features) {
                        if (f == null) continue;
                        String u = f.toUpperCase();
                        if (u.contains("ROLE") && u.contains("COLOR")) {
                            supported = true;
                            break;
                        }
                        if (u.equals("ENHANCED_ROLE_COLORS") || u.equals("GUILD_ROLE_COLORS") || u.equals("ROLE_COLORS")) {
                            supported = true;
                            break;
                        }
                    }
                }
                // Update cache
                capabilityCache.put(guildId, new CacheEntry(supported, System.currentTimeMillis() + CAPABILITY_TTL_MILLIS));
                return supported;
            } else {
                log.warn("Discord API GET /guilds/{} returned status {}", guildId, resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to check guild features for {}: {}", guildId, e.toString());
        }
        return false;
    }

    /** Evict a single guild's cached capability entry. */
    public void evictGuildCapability(String guildId) {
        capabilityCache.remove(guildId);
    }

    /** Clear all cached capability entries. */
    public void clearCapabilityCache() {
        capabilityCache.clear();
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

    /**
     * Create a role in a guild using Discord's REST API with Role Colors Object support.
     * Returns the created role ID on success, or null on failure.
     *
     * @param guildId The guild ID
     * @param name Full role name (e.g., "gacha:epic:Ocean Dream")
     * @param primaryColor Primary color as RGB integer (required)
     * @param secondaryColor Secondary color for gradient (optional, can be null)
     * @param tertiaryColor Tertiary color for holographic (optional, can be null)
     * @param mentionable Whether the role is mentionable
     * @param hoist Whether the role is displayed separately
     * @return The created role ID, or null on failure
     */
    public String createRoleWithColors(String guildId, String name, int primaryColor,
                                      Integer secondaryColor, Integer tertiaryColor,
                                      boolean mentionable, boolean hoist) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Cannot create role: DISCORD_TOKEN not set");
            return null;
        }
        
        try {
            String url = DISCORD_API_BASE + "/guilds/" + guildId + "/roles";
            
            // Build colors object per Discord API docs
            Map<String, Object> colorsObj = new HashMap<>();
            colorsObj.put("primary_color", primaryColor);
            if (secondaryColor != null) {
                colorsObj.put("secondary_color", secondaryColor);
            }
            if (tertiaryColor != null) {
                colorsObj.put("tertiary_color", tertiaryColor);
            }
            
            // Build request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("colors", colorsObj);
            payload.put("mentionable", mentionable);
            payload.put("hoist", hoist);
            
            String jsonPayload = mapper.writeValueAsString(payload);
            
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bot " + botToken)
                    .header("User-Agent", "Playbot (github.com/wraithfive/playbot)")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
            
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                ApiRole created = mapper.readValue(resp.body(), ApiRole.class);
                log.info("Created role {} in guild {} via API", name, guildId);
                return created.id;
            } else {
                log.warn("Discord API POST /guilds/{}/roles returned status {}: {}", 
                    guildId, resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.error("Failed to create role {} in guild {}: {}", name, guildId, e.toString());
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ApiRole {
        @JsonProperty("id")
        public String id;

        @JsonProperty("colors")
        public RoleColors colors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ApiGuild {
        @JsonProperty("id")
        public String id;

        @JsonProperty("features")
        public List<String> features;
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

class CacheEntry {
    final boolean value;
    final long expiresAt;
    CacheEntry(boolean value, long expiresAt) {
        this.value = value;
        this.expiresAt = expiresAt;
    }
}
