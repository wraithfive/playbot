package com.discordbot;

import com.discordbot.web.config.WebSocketSecurityInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketSecurityInterceptorTest {

    @Test
    @DisplayName("Rejects CONNECT without authentication")
    void rejectWithoutAuth() {
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        org.springframework.messaging.MessageChannel channel = mock(org.springframework.messaging.MessageChannel.class);
        assertNull(interceptor.preSend(msg, channel));
    }

    @Test
    @DisplayName("Accepts CONNECT with authenticated user")
    void acceptWithAuth() {
        WebSocketSecurityInterceptor interceptor = new WebSocketSecurityInterceptor();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("user123");
        accessor.setUser(auth);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        org.springframework.messaging.MessageChannel channel = mock(org.springframework.messaging.MessageChannel.class);
        assertNotNull(interceptor.preSend(msg, channel));
    }
}
