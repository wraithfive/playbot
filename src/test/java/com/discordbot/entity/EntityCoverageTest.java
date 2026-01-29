package com.discordbot.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EntityCoverageTest {

    @Test
    void testQotdQuestion_GettersSetters() {
        QotdQuestion q1 = new QotdQuestion("g1", "c1", "text1");
        assertEquals("g1", q1.getGuildId());
        assertEquals("c1", q1.getChannelId());
        assertEquals("text1", q1.getText());
        assertNotNull(q1.getCreatedAt());

        QotdQuestion q2 = new QotdQuestion("g2", "c2", "text2", "u2", "user2");
        q2.setId(42L);
        Instant now = Instant.now();
        q2.setCreatedAt(now);
        q2.setGuildId("g2x");
        q2.setChannelId("c2x");
        q2.setText("text2x");
        q2.setAuthorUserId("u2x");
        q2.setAuthorUsername("user2x");

        assertEquals(42L, q2.getId());
        assertEquals(now, q2.getCreatedAt());
        assertEquals("g2x", q2.getGuildId());
        assertEquals("c2x", q2.getChannelId());
        assertEquals("text2x", q2.getText());
        assertEquals("u2x", q2.getAuthorUserId());
        assertEquals("user2x", q2.getAuthorUsername());
    }

    @Test
    void testQotdSubmission_GettersSetters() {
        QotdSubmission s = new QotdSubmission("g1", "u1", "user1", "q?");
        assertEquals("g1", s.getGuildId());
        assertEquals("u1", s.getUserId());
        assertEquals("user1", s.getUsername());
        assertEquals("q?", s.getText());
        assertEquals(QotdSubmission.Status.PENDING, s.getStatus());
        assertNotNull(s.getCreatedAt());

        s.setStatus(QotdSubmission.Status.APPROVED);
        s.setApprovedByUserId("admin");
        s.setApprovedByUsername("Admin");
        Instant approvedAt = Instant.now();
        s.setApprovedAt(approvedAt);
        s.setGuildId("g2");
        s.setUserId("u2");
        s.setUsername("user2");
        s.setText("new");

        assertEquals(QotdSubmission.Status.APPROVED, s.getStatus());
        assertEquals("admin", s.getApprovedByUserId());
        assertEquals("Admin", s.getApprovedByUsername());
        assertEquals(approvedAt, s.getApprovedAt());
        assertEquals("g2", s.getGuildId());
        assertEquals("u2", s.getUserId());
        assertEquals("user2", s.getUsername());
        assertEquals("new", s.getText());
    }
}
