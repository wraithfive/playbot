package com.discordbot;

import com.discordbot.web.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test rate limiting functionality
 */
public class RateLimitServiceTest {

    @Test
    public void testBulkOperationRateLimit() {
        RateLimitService rateLimitService = new RateLimitService();

        // Create mock authentication
        Authentication auth = createMockAuth("123456789");

        // First 5 requests should succeed (limit is 5 per minute)
        for (int i = 0; i < 5; i++) {
            boolean allowed = rateLimitService.allowBulkOperation(auth);
            assertTrue(allowed, "Request " + (i + 1) + " should be allowed");
        }

        // 6th request should be denied
        boolean denied = rateLimitService.allowBulkOperation(auth);
        assertFalse(denied, "Request 6 should be denied due to rate limit");

        // Verify available tokens
        long available = rateLimitService.getAvailableTokens(auth, "bulk");
        assertEquals(0, available, "Should have 0 tokens remaining after exceeding limit");
    }

    @Test
    public void testDifferentUsersHaveSeparateLimits() {
        RateLimitService rateLimitService = new RateLimitService();

        Authentication user1 = createMockAuth("111111111");
        Authentication user2 = createMockAuth("222222222");

        // User 1 uses all 5 tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.allowBulkOperation(user1));
        }
        assertFalse(rateLimitService.allowBulkOperation(user1), "User 1 should be rate limited");

        // User 2 should still have tokens available
        assertTrue(rateLimitService.allowBulkOperation(user2), "User 2 should not be affected by User 1's rate limit");
    }

    @Test
    public void testNullAuthenticationFailsOpen() {
        RateLimitService rateLimitService = new RateLimitService();

        // Null authentication should fail open (allow request)
        boolean allowed = rateLimitService.allowBulkOperation(null);
        assertTrue(allowed, "Null authentication should fail open and allow request");
    }

    private Authentication createMockAuth(String userId) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", userId);
        attributes.put("username", "testuser");

        OAuth2User oauth2User = new DefaultOAuth2User(
                java.util.Collections.emptyList(),
                attributes,
                "id"
        );

        when(auth.getPrincipal()).thenReturn(oauth2User);
        return auth;
    }
}
