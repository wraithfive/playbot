package com.discordbot;

import com.discordbot.entity.QotdConfig;
import com.discordbot.entity.QotdQuestion;
import com.discordbot.entity.QotdSubmission;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation") // Tests deprecated QotdConfig entity
class QotdEntitiesTest {

    @Test
    void qotdConfigIdEqualsHashCode() {
        QotdConfig.QotdConfigId a = new QotdConfig.QotdConfigId("g1", "c1");
        QotdConfig.QotdConfigId b = new QotdConfig.QotdConfigId("g1", "c1");
        QotdConfig.QotdConfigId c = new QotdConfig.QotdConfigId("g1", "c2");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void qotdQuestionConstructorsAndGetters() {
        QotdQuestion q1 = new QotdQuestion("g1", "c1", "text1");
        assertEquals("g1", q1.getGuildId());
        assertEquals("c1", q1.getChannelId());
        assertEquals("text1", q1.getText());
        assertNotNull(q1.getCreatedAt());

        QotdQuestion q2 = new QotdQuestion("g1", "c1", "text2", "u1", "alice");
        assertEquals("u1", q2.getAuthorUserId());
        assertEquals("alice", q2.getAuthorUsername());
    }

    @Test
    void qotdSubmissionLifecycle() {
        QotdSubmission s = new QotdSubmission("g1", "u1", "alice", "question?");
        assertEquals(QotdSubmission.Status.PENDING, s.getStatus());
        assertNotNull(s.getCreatedAt());

        s.setStatus(QotdSubmission.Status.APPROVED);
        s.setApprovedByUserId("mod1");
        s.setApprovedByUsername("mod");
        Instant now = Instant.now();
        s.setApprovedAt(now);

        assertEquals(QotdSubmission.Status.APPROVED, s.getStatus());
        assertEquals("mod1", s.getApprovedByUserId());
        assertEquals("mod", s.getApprovedByUsername());
        assertEquals(now, s.getApprovedAt());
    }
}
