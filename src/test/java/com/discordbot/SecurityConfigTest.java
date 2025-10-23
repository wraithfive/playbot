package com.discordbot;

import com.discordbot.web.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.sql.DataSource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityConfigTest {

    @Test
    @DisplayName("corsConfigurationSource builds config for origin")
    void cors_source() {
        SecurityConfig cfg = new SecurityConfig();
        CorsConfigurationSource source = cfg.corsConfigurationSource("http://example.com");
        assertNotNull(source);
        assertTrue(source instanceof UrlBasedCorsConfigurationSource);
        UrlBasedCorsConfigurationSource urlSource = (UrlBasedCorsConfigurationSource) source;
        CorsConfiguration config = urlSource.getCorsConfigurations().get("/**");
        assertNotNull(config);
        var allowed = config.getAllowedOrigins();
        assertNotNull(allowed);
        assertTrue(allowed.contains("http://example.com"));
    }

    @Test
    @DisplayName("requestDebugLoggingFilter calls next in chain")
    void request_logging_filter() throws Exception {
        SecurityConfig cfg = new SecurityConfig();
        var filter = cfg.requestDebugLoggingFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));
        when(req.getScheme()).thenReturn("http");
        when(req.getServerName()).thenReturn("localhost");
        when(req.getServerPort()).thenReturn(80);
        filter.doFilter(req, res, chain);
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    @DisplayName("requestDebugLoggingFilter logs when DEBUG is enabled")
    void request_logging_filter_debug_branch() throws Exception {
        SecurityConfig cfg = new SecurityConfig();
        var filter = cfg.requestDebugLoggingFilter();

        // Bump logger for SecurityConfig to DEBUG to exercise branch
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);
        ch.qos.logback.classic.Level previous = logger.getLevel();
        try {
            logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

            HttpServletRequest req = mock(HttpServletRequest.class);
            HttpServletResponse res = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            when(req.getRequestURL()).thenReturn(new StringBuffer("https://example.com/a"));
            when(req.getScheme()).thenReturn("https");
            when(req.getServerName()).thenReturn("example.com");
            when(req.getServerPort()).thenReturn(443);
            when(req.getHeader("X-Forwarded-Proto")).thenReturn("https");
            when(req.getHeader("X-Forwarded-Host")).thenReturn("proxy.example.com");
            when(req.getHeader("X-Forwarded-Port")).thenReturn("443");

            filter.doFilter(req, res, chain);
            verify(chain, times(1)).doFilter(req, res);
        } finally {
            logger.setLevel(previous);
        }
    }

    @Test
    @DisplayName("authorizedClientService bean creates JDBC service")
    void auth_client_service() {
        SecurityConfig cfg = new SecurityConfig();
        DataSource ds = mock(DataSource.class);
        ClientRegistrationRepository repo = mock(ClientRegistrationRepository.class);
        OAuth2AuthorizedClientService service = cfg.authorizedClientService(ds, repo);
        assertNotNull(service);
    }
}
