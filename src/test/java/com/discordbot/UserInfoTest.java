package com.discordbot;

import com.discordbot.web.dto.UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserInfoTest {

    @Test
    @DisplayName("UserInfo record accessors and value semantics")
    void userInfoRecord() {
        UserInfo u1 = new UserInfo("123", "alice", "0001", "abc123");
        UserInfo u2 = new UserInfo("123", "alice", "0001", "abc123");

        assertEquals("123", u1.id());
        assertEquals("alice", u1.username());
        assertEquals("0001", u1.discriminator());
        assertEquals("abc123", u1.avatar());

        assertEquals(u1, u2);
        assertEquals(u1.hashCode(), u2.hashCode());
        assertTrue(u1.toString().contains("alice"));
    }
}
