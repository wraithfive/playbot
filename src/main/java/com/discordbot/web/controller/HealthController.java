package com.discordbot.web.controller;

import net.dv8tion.jda.api.JDA;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final JDA jda;

    public HealthController(JDA jda) {
        this.jda = jda;
    }

    /**
     * GET /api/health
     * Public endpoint to check if the service is running
     * NO AUTHENTICATION REQUIRED
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("bot", Map.of(
            "connected", jda.getStatus().name(),
            "username", jda.getSelfUser().getName(),
            "guilds", jda.getGuilds().size()
        ));
        health.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(health);
    }

    /**
     * GET /api/csrf
     * Endpoint to trigger CSRF token generation
     * REQUIRES AUTHENTICATION - this ensures CSRF token is set
     */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(org.springframework.security.web.csrf.CsrfToken csrfToken) {
        // Simply accepting CsrfToken as parameter causes Spring to generate and set the cookie
        return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
    }
}
