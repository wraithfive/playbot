package com.discordbot.web.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Centralized cache for Discord user guilds, shared between services and listeners.
 * This breaks bean cycles by decoupling cache eviction from AdminService.
 */
@Component
public class GuildsCache {

    private static final Logger logger = LoggerFactory.getLogger(GuildsCache.class);

    private final Cache<String, List<Map<String, Object>>> guildsCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .maximumSize(1000)
            .recordStats()
            .build();

    /**
     * Get cached guilds by token, or fetch using provided loader on miss.
     */
    public List<Map<String, Object>> get(String accessToken,
                                         Function<String, List<Map<String, Object>>> loader) {
        return guildsCache.get(accessToken, key -> {
            logger.debug("Cache miss - fetching guilds from Discord API");
            List<Map<String, Object>> result = loader.apply(key);
            logger.debug("Fetched and cached {} guilds (TTL: 10 seconds)", result != null ? result.size() : 0);
            return result;
        });
    }

    /** Evict cache entry for a specific access token. */
    public void evictForToken(String accessToken) {
        if (accessToken != null) {
            guildsCache.invalidate(accessToken);
            // Log only a fingerprint to avoid exposing sensitive tokens
            logger.info("Evicted guilds cache for token ending in: {}", 
                accessToken.length() > 4 ? "..." + accessToken.substring(accessToken.length() - 4) : "****");
        }
    }

    /** Evict all cache entries. */
    public void evictAll() {
        guildsCache.invalidateAll();
        logger.info("Evicted all guilds cache entries");
    }
}
