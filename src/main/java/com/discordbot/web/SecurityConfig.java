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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/", "/index.html", "/static/**", "/favicon.ico", "/manifest.json").permitAll()
                .requestMatchers("/api/health").permitAll()
                // OAuth2 login endpoints
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("http://localhost:3000", true)
                .failureUrl("http://localhost:3000/login?error=true")
            )
            .logout(logout -> logout
                .logoutSuccessUrl("http://localhost:3000")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    /**
     * Simple request-logging filter to help debug what the app sees when building redirect URIs.
     * Logs scheme, server name, server port, request URL and common X-Forwarded headers at DEBUG.
     */
    @Bean
    public Filter requestDebugLoggingFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, java.io.IOException {
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:8080"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
