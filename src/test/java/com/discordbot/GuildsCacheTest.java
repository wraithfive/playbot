package com.discordbot;

import com.discordbot.web.service.GuildsCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GuildsCacheTest {

    @Test
    @DisplayName("GuildsCache caches loader results per token and supports eviction")
    void cacheAndEvict() {
        GuildsCache cache = new GuildsCache();
        AtomicInteger calls = new AtomicInteger();

        var loader = (java.util.function.Function<String, List<Map<String, Object>>>) token -> {
            calls.incrementAndGet();
            List<Map<String, Object>> list = new ArrayList<>();
            list.add(Map.of("id", "g1", "name", "Guild One"));
            return list;
        };

        // First call should invoke loader
        List<Map<String, Object>> first = cache.get("tokenA", loader);
        assertEquals(1, calls.get());
        assertEquals(1, first.size());

        // Second call with same tokenA should use cache
        List<Map<String, Object>> second = cache.get("tokenA", loader);
        assertSame(first, second);
        assertEquals(1, calls.get());

        // Different token should call loader again
        cache.get("tokenB", loader);
        assertEquals(2, calls.get());

        // Evict tokenA and ensure reload occurs
        cache.evictForToken("tokenA");
        cache.get("tokenA", loader);
        assertEquals(3, calls.get());

        // Evict all and ensure reload occurs for both tokens
        cache.evictAll();
        cache.get("tokenA", loader);
        cache.get("tokenB", loader);
        assertEquals(5, calls.get());
    }
}
