package com.discordbot;

import com.discordbot.entity.UserCooldown;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserCooldownTest {

    @Test
    void gettersSettersAndToString() {
        LocalDateTime now = LocalDateTime.now();
        UserCooldown uc = new UserCooldown("u1", "g1", now, "alice");
        uc.setId(42L);

        assertEquals(42L, uc.getId());
        assertEquals("u1", uc.getUserId());
        assertEquals("g1", uc.getGuildId());
        assertEquals(now, uc.getLastRollTime());
        assertEquals("alice", uc.getUsername());

        String s = uc.toString();
        assertTrue(s.contains("UserCooldown"));
        assertTrue(s.contains("u1"));
        assertTrue(s.contains("g1"));
    }
}
