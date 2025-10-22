package com.discordbot.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;

/**
 * WebSocket security interceptor to ensure only authenticated users can connect.
 * Validates that users have a valid session before establishing WebSocket connection.
 */
@Configuration
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketSecurityInterceptor.class);

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Get the authentication from the session
            Authentication authentication = (Authentication) accessor.getUser();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                logger.warn("Unauthorized WebSocket connection attempt");
                // Return null to reject the connection
                return null;
            }
            
            logger.info("Authenticated WebSocket connection established for user: {}", 
                authentication.getName());
        }
        
        return message;
    }
}
