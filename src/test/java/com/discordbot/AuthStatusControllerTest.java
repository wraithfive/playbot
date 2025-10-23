package com.discordbot;

import com.discordbot.web.AuthStatusController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthStatusControllerTest {

    @Test
    @DisplayName("/api/auth/status returns authenticated=false when no principal")
    void status_unauthenticated() {
        AuthStatusController controller = new AuthStatusController();
        Map<String, Object> res = controller.status(null);
        assertNotNull(res);
        assertEquals(Boolean.FALSE, res.get("authenticated"));
        assertFalse(res.containsKey("name"));
    }

    @Test
    @DisplayName("/api/auth/status returns authenticated=true and name when principal present")
    void status_authenticated() {
        AuthStatusController controller = new AuthStatusController();
        Principal principal = () -> "test-user";
        Map<String, Object> res = controller.status(principal);
        assertNotNull(res);
        assertEquals(Boolean.TRUE, res.get("authenticated"));
        assertEquals("test-user", res.get("name"));
    }
}
