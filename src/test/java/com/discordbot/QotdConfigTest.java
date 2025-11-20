package com.discordbot;

import com.discordbot.entity.QotdConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation") // Tests deprecated QotdConfig entity
class QotdConfigTest {

    @Test
    @DisplayName("QotdConfig getters/setters and defaults work")
    void gettersSetters() {
        QotdConfig cfg = new QotdConfig();
        cfg.setGuildId("g1");
        cfg.setChannelId("c1");
        cfg.setEnabled(true);
        cfg.setScheduleCron("0 9 * * MON,WED,FRI");
        cfg.setTimezone("America/New_York");
        cfg.setRandomize(true);
        Instant now = Instant.now();
        cfg.setLastPostedAt(now);
        cfg.setNextIndex(5);

        assertEquals("g1", cfg.getGuildId());
        assertEquals("c1", cfg.getChannelId());
        assertTrue(cfg.isEnabled());
        assertEquals("0 9 * * MON,WED,FRI", cfg.getScheduleCron());
        assertEquals("America/New_York", cfg.getTimezone());
        assertTrue(cfg.isRandomize());
        assertEquals(now, cfg.getLastPostedAt());
        assertEquals(5, cfg.getNextIndex());
    }

    @Test
    @DisplayName("QotdConfigId equals/hashCode symmetry and inequality")
    void idEqualsHashCode() {
        QotdConfig.QotdConfigId id1 = new QotdConfig.QotdConfigId("g1", "c1");
        QotdConfig.QotdConfigId id2 = new QotdConfig.QotdConfigId("g1", "c1");
        QotdConfig.QotdConfigId id3 = new QotdConfig.QotdConfigId("g1", "c2");

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
        assertNotEquals(id1, id3);
        assertNotEquals(id1, null);
        assertNotEquals(id1, new Object());
    }

    @Test
    @DisplayName("QotdConfigId getters and setters work")
    void idGettersSetters() {
        QotdConfig.QotdConfigId id = new QotdConfig.QotdConfigId();
        id.setGuildId("gX");
        id.setChannelId("cY");
        assertEquals("gX", id.getGuildId());
        assertEquals("cY", id.getChannelId());
    }
}
