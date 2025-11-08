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
     * Results are cached for 10 minutes to reduce API calls.
     * 
     * <p>This method never throws; returns false on failures or when token is missing.
     *
     * @param guildId The Discord guild ID
     * @return true if the guild supports enhanced role colors, false otherwise
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

    /**
     * Evict a single guild's cached capability entry.
     * 
     * <p>Use this when you know a guild's features may have changed
     * and want to force a fresh capability check on the next call.
     *
     * @param guildId The Discord guild ID to evict from cache
     */
    public void evictGuildCapability(String guildId) {
        capabilityCache.remove(guildId);
    }

    /**
     * Clear all cached capability entries.
     * 
     * <p>Useful when refreshing user session or performing bulk operations
     * where guild features may have changed across multiple guilds.
     */
    public void clearCapabilityCache() {
        capabilityCache.clear();
    }

    /**
     * Fetch roles for a guild and return a map of roleId to RoleColors object.
     * 
     * <p>This method never throws; returns an empty map on failures.
     *
     * @param guildId The Discord guild ID
     * @return Map of role ID to RoleColors, or empty map if unavailable
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
     * @param secondaryColor Secondary color for gradient (use {@link RoleColors#COLOR_NOT_SET} for solid color)
     * @param tertiaryColor Tertiary color for holographic (use {@link RoleColors#COLOR_NOT_SET} for solid/gradient)
     * @param mentionable Whether the role is mentionable
     * @param hoist Whether the role is displayed separately
     * @return The created role ID, or null on failure
     */
    public String createRoleWithColors(String guildId, String name, int primaryColor,
                                      int secondaryColor, int tertiaryColor,
                                      boolean mentionable, boolean hoist) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Cannot create role: DISCORD_TOKEN not set");
            return null;
        }
        
        try {
            String url = DISCORD_API_BASE + "/guilds/" + guildId + "/roles";
            
            // Build colors object per Discord API docs
            Map<String, Object> colorsObj = buildColorsObject(primaryColor, secondaryColor, tertiaryColor);
            
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

        /**
     * Build a colors object for the Discord API request payload.
     * 
     * <p>Creates a map with primary_color always set, and optionally
     * secondary_color and tertiary_color if they are not sentinel values.
     *
     * @param primaryColor Primary RGB color (always required)
     * @param secondaryColor Secondary RGB color ({@link RoleColors#COLOR_NOT_SET} for solid)
     * @param tertiaryColor Tertiary RGB color ({@link RoleColors#COLOR_NOT_SET} for solid/gradient)
     * @return Map ready for JSON serialization
     */
    private Map<String, Object> buildColorsObject(int primaryColor, int secondaryColor, int tertiaryColor) {
        Map<String, Object> colorsObj = new HashMap<>();
        colorsObj.put("primary_color", primaryColor);
        if (secondaryColor != RoleColors.COLOR_NOT_SET) {
            colorsObj.put("secondary_color", secondaryColor);
        }
        if (tertiaryColor != RoleColors.COLOR_NOT_SET) {
            colorsObj.put("tertiary_color", tertiaryColor);
        }
        return colorsObj;
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
     * Represents Discord's Role Colors Object for gradient and holographic roles.
     * 
     * <p>Stores colors as primitives to reduce memory overhead per JDA best practices.
     * Colors are lazily initialized from Jackson's deserialization objects.
     * 
     * <p>The primary color is always present (defaults to {@link #DEFAULT_COLOR_RAW}).
     * Secondary and tertiary colors are optional and indicate gradient/holographic styles.
     *
     * @see <a href="https://discord.com/developers/docs/topics/permissions#role-object-role-colors-object">Discord Role Colors Object</a>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoleColors {
        /** Discord's default color value when no color is set */
        public static final int DEFAULT_COLOR_RAW = 0;
        
        /** Magic value Discord uses for holographic primary color */
        public static final int HOLOGRAPHIC_PRIMARY = 11127295;
        
        /** Magic value Discord uses for holographic secondary color */
        public static final int HOLOGRAPHIC_SECONDARY = 16759788;
        
        /** Magic value Discord uses for holographic tertiary color */
        public static final int HOLOGRAPHIC_TERTIARY = 16761760;
        
        /** Sentinel value indicating color is not set */
        public static final int COLOR_NOT_SET = -1;

        @JsonProperty("primary_color")
        private int primaryColor;

        @JsonProperty("secondary_color")
        private Integer secondaryColorObj; // Keep as Integer for Jackson deserialization

        @JsonProperty("tertiary_color")
        private Integer tertiaryColorObj; // Keep as Integer for Jackson deserialization
        
        // Store as primitives internally after deserialization for memory efficiency
        private transient int secondaryColor = COLOR_NOT_SET;
        private transient int tertiaryColor = COLOR_NOT_SET;
        private transient boolean initialized = false;

        /**
         * Ensure colors are initialized from Jackson objects to primitive fields.
         * Called lazily on first access to secondary/tertiary colors.
         */
        private void ensureInitialized() {
            if (!initialized) {
                this.secondaryColor = secondaryColorObj != null ? secondaryColorObj : COLOR_NOT_SET;
                this.tertiaryColor = tertiaryColorObj != null ? tertiaryColorObj : COLOR_NOT_SET;
                this.initialized = true;
            }
        }

        /**
         * Get the primary color as RGB integer.
         * 
         * <p>Defaults to {@link #DEFAULT_COLOR_RAW} if this role has no set color.
         * For holographic roles, this returns {@link #HOLOGRAPHIC_PRIMARY}.
         *
         * @return The primary color RGB value
         */
        public int getPrimaryColor() {
            return primaryColor;
        }

        /**
         * Get the secondary color as RGB integer.
         * 
         * <p>Returns {@code -1} if not set (solid color role).
         * For gradient roles, this provides the second color stop.
         * For holographic roles, this returns {@link #HOLOGRAPHIC_SECONDARY}.
         *
         * @return The secondary color RGB value, or -1 if not set
         */
        public int getSecondaryColor() {
            ensureInitialized();
            return secondaryColor;
        }

        /**
         * Get the tertiary color as RGB integer.
         * 
         * <p>Returns {@code -1} if not set (solid or gradient role).
         * Only holographic roles have a tertiary color set to {@link #HOLOGRAPHIC_TERTIARY}.
         *
         * @return The tertiary color RGB value, or -1 if not set
         */
        public int getTertiaryColor() {
            ensureInitialized();
            return tertiaryColor;
        }

        /**
         * Check if this role has no color set.
         *
         * @return true if the primary color is the default (unset)
         */
        public boolean isUnset() {
            return primaryColor == DEFAULT_COLOR_RAW;
        }

        /**
         * Check if this role uses gradient coloring.
         * 
         * <p>A role is considered gradient if it has a secondary color but no tertiary.
         *
         * @return true if this is a gradient role
         */
        public boolean isGradient() {
            ensureInitialized();
            return secondaryColor != COLOR_NOT_SET && tertiaryColor == COLOR_NOT_SET;
        }

        /**
         * Check if this role uses holographic coloring.
         * 
         * <p>A role is considered holographic if it has all three colors set.
         *
         * @return true if this is a holographic role
         */
        public boolean isHolographic() {
            ensureInitialized();
            return tertiaryColor != COLOR_NOT_SET;
        }
    }

    // Internal cache entry for boolean values with expiration
    private static class CacheEntry {
        final boolean value;
        final long expiresAt;
        CacheEntry(boolean value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
