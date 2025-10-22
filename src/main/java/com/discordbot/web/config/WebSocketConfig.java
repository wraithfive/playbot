package com.discordbot.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time guild updates.
 * Allows the backend to push notifications to the frontend when the bot joins/leaves guilds.
 * Requires authentication via session cookies.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketSecurityInterceptor webSocketSecurityInterceptor;

    public WebSocketConfig(WebSocketSecurityInterceptor webSocketSecurityInterceptor) {
        this.webSocketSecurityInterceptor = webSocketSecurityInterceptor;
    }

    /**
     * Resolves the admin panel origin for WebSocket CORS.
     * Falls back to local dev server if ADMIN_PANEL_URL is not set.
     */
    private String getAdminPanelUrl() {
        String adminPanelUrl = System.getenv("ADMIN_PANEL_URL");
        return adminPanelUrl != null ? adminPanelUrl : "http://localhost:3000";
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        // Add security interceptor to validate authentication on WebSocket connections
        registration.interceptors(webSocketSecurityInterceptor);
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker to send messages to clients
        // subscribed to destinations prefixed with "/topic"
        config.enableSimpleBroker("/topic");
        
        // Messages sent to destinations prefixed with "/app" will be routed to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        String adminPanelUrl = getAdminPanelUrl();
        
        // Register the /ws endpoint for WebSocket connections
        // withSockJS() provides fallback options for browsers that don't support WebSocket
        // Note: Authentication will be verified via session cookies when the connection is established
        registry.addEndpoint("/ws")
                .setAllowedOrigins(adminPanelUrl) // Use specific origin for security in production
                .withSockJS();
    }
}
