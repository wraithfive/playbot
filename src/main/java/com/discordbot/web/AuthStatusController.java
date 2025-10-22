package com.discordbot.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import java.util.Map;

@RestController
public class AuthStatusController {
    @GetMapping("/api/auth/status")
    public Map<String, Object> status(@AuthenticationPrincipal Principal principal) {
        if (principal == null) {
            return Map.of("authenticated", false);
        }
        String name = principal.getName();
        return Map.of(
            "authenticated", true,
            "name", name
        );
    }
}
