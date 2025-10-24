package com.discordbot;

import com.discordbot.entity.UserCooldown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserCooldownTest {

    @Test
    @DisplayName("UserCooldown getters, setters, and toString")
    void userCooldown_basic() {
        LocalDateTime t = LocalDateTime.now().minusHours(1);
        UserCooldown uc = new UserCooldown();
        uc.setId(42L);
        uc.setUserId("u1");
        uc.setGuildId("g1");
        uc.setLastRollTime(t);
        uc.setUsername("Alice");

        assertEquals(42L, uc.getId());
        assertEquals("u1", uc.getUserId());
        assertEquals("g1", uc.getGuildId());
        assertEquals(t, uc.getLastRollTime());
        assertEquals("Alice", uc.getUsername());

        String s = uc.toString();
        assertNotNull(s);
        assertTrue(s.contains("u1"));
        assertTrue(s.contains("g1"));
    }
}

