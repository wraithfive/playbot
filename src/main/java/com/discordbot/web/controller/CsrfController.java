package com.discordbot.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.Map;

/**
 * Exposes a CSRF endpoint for SPA clients. Hitting this endpoint ensures the
 * CookieCsrfTokenRepository generates and sets the XSRF-TOKEN cookie. The
 * token is also returned in the body for convenience.
 */
@RestController
public class CsrfController {

    @GetMapping("/api/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok(Map.of(
            "headerName", token.getHeaderName(),
            "parameterName", token.getParameterName(),
            "token", token.getToken()
        ));
    }
}
