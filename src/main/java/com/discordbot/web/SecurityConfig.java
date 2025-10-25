package com.discordbot.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.ForwardedHeaderFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import javax.sql.DataSource;
import org.springframework.lang.NonNull;

/**
 * Spring Security configuration for the Playbot admin API.
 *
 * Responsibilities:
 * - CORS/CSRF configuration for the React admin panel
 * - OAuth2 Login with Discord and redirect back to the admin panel
 * - Persistent storage of OAuth2 authorized clients (incl. refresh tokens)
 * - Persistent HTTP sessions via Spring Session (configured in properties)
 * - Optional request logging to help diagnose reverse-proxy header issues
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Resolves the admin panel origin for CORS and OAuth2 redirects.
     * Falls back to local dev server if ADMIN_PANEL_URL is not set.
     */
    private String getAdminPanelUrl() {
        String adminPanelUrl = System.getenv("ADMIN_PANEL_URL");
        return adminPanelUrl != null ? adminPanelUrl : "http://localhost:3000";
    }

    /**
     * Primary security filter chain.
     * - Enables CORS with the admin panel origin
     * - Uses CookieCsrfTokenRepository to enable SPA-friendly CSRF protection
     * - Permits health and OAuth endpoints; secures /api/**
     * - Configures OAuth2 Login with Discord
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        final String adminPanelUrl = getAdminPanelUrl();

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource(adminPanelUrl)))
            .csrf(csrf -> csrf
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/ws/**") // Disable CSRF for WebSocket endpoints
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/static/**", "/favicon.ico", "/manifest.json").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/csrf").permitAll()
                .requestMatchers("/ws/**").authenticated() // Require authentication for WebSocket connections
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                    .requestMatchers("/api/servers/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl(adminPanelUrl, true)
                .failureUrl(adminPanelUrl + "/login?error=true")
            )
            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .logoutSuccessUrl(adminPanelUrl)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }

    /**
     * Allows Spring to respect X-Forwarded-* headers when behind a reverse proxy
     * (e.g., Nginx, Cloudflare, Docker ingress), so redirect URIs are built correctly.
     */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    /**
     * Persists OAuth2 authorized clients (including refresh tokens) so admins
     * stay logged in across restarts.
     *
     * Storage uses Spring's JDBC schema. Spring Boot will auto-config schema creation
     * if enabled, or you can initialize it manually using Spring Security's DDL.
     */
    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(DataSource dataSource, ClientRegistrationRepository registrations) {
        return new JdbcOAuth2AuthorizedClientService(new JdbcTemplate(dataSource), registrations);
    }

    /**
     * Low-overhead request logger to help troubleshoot origin/forwarded header issues.
     * Only emits logs when DEBUG level is enabled.
     */
    @Bean
    public Filter requestDebugLoggingFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, java.io.IOException {
                if (org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).isDebugEnabled()) {
                    StringBuffer url = request.getRequestURL();
                    String forwardedProto = request.getHeader("X-Forwarded-Proto");
                    String forwardedHost = request.getHeader("X-Forwarded-Host");
                    String forwardedPort = request.getHeader("X-Forwarded-Port");
                    org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).debug("Request URL: {} | scheme={} | server={}:{} | X-Forwarded-Proto={} | X-Forwarded-Host={} | X-Forwarded-Port={}",
                            url, request.getScheme(), request.getServerName(), request.getServerPort(), forwardedProto, forwardedHost, forwardedPort);
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    /**
     * CORS configuration that whitelists the admin panel origin and supports credentials.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(String adminPanelUrl) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(adminPanelUrl));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
