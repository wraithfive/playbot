package com.discordbot.web.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Rate limiting service using Bucket4j and Caffeine cache.
 * Protects bulk operations from abuse by limiting requests per user.
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    private final Cache<String, Bucket> buckets;

    // Rate limit configurations
    private static final int BULK_OPERATIONS_LIMIT = 5; // requests
    private static final Duration BULK_OPERATIONS_WINDOW = Duration.ofMinutes(1); // per minute

    public RateLimitService() {
        // Create Caffeine cache for storing buckets
        this.buckets = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    /**
     * Check if a bulk operation is allowed for the given user.
     * 
     * @param authentication The authenticated user
     * @return true if operation is allowed, false if rate limit exceeded
     */
    public boolean allowBulkOperation(Authentication authentication) {
        String userId = getUserId(authentication);
        if (userId == null) {
            logger.warn("Cannot apply rate limit: user ID not found");
            return true; // Fail open - don't block if we can't identify user
        }

        String key = "bulk:" + userId;
        Bucket bucket = buckets.get(key, k -> createBulkOperationBucket());

        boolean allowed = bucket.tryConsume(1);
        
        if (!allowed) {
            logger.warn("Rate limit exceeded for bulk operation: userId={}", userId);
        } else {
            logger.debug("Bulk operation allowed: userId={}, remaining={}", 
                        userId, bucket.getAvailableTokens());
        }

        return allowed;
    }

    /**
     * Get available tokens for a user (for debugging/monitoring).
     */
    public long getAvailableTokens(Authentication authentication, String operation) {
        String userId = getUserId(authentication);
        if (userId == null) return -1;

        String key = operation + ":" + userId;
        Bucket bucket = buckets.getIfPresent(key);
        return bucket != null ? bucket.getAvailableTokens() : BULK_OPERATIONS_LIMIT;
    }

    private Bucket createBulkOperationBucket() {
        // Token bucket: 5 tokens, refill 5 tokens per minute (simple refill)
        Bandwidth limit = Bandwidth.builder()
                .capacity(BULK_OPERATIONS_LIMIT)
                .refillIntervally(BULK_OPERATIONS_LIMIT, BULK_OPERATIONS_WINDOW)
                .build();
        
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String getUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            return oauth2User.getAttribute("id");
        }

        return authentication.getName();
    }
}
