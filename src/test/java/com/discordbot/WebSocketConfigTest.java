package com.discordbot;

import com.discordbot.web.config.WebSocketConfig;
import com.discordbot.web.config.WebSocketSecurityInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class WebSocketConfigTest {

    @Test
    @DisplayName("Registers interceptor, broker, and /ws endpoint")
    void websocket_config_methods() {
        WebSocketSecurityInterceptor interceptor = mock(WebSocketSecurityInterceptor.class);
        WebSocketConfig config = new WebSocketConfig(interceptor);

        ChannelRegistration registration = mock(ChannelRegistration.class);
        when(registration.interceptors(interceptor)).thenReturn(registration);
        config.configureClientInboundChannel(registration);
        verify(registration, times(1)).interceptors(interceptor);

        MessageBrokerRegistry broker = mock(MessageBrokerRegistry.class);
        config.configureMessageBroker(broker);
        verify(broker).enableSimpleBroker("/topic");
        verify(broker).setApplicationDestinationPrefixes("/app");

        StompEndpointRegistry endpoints = mock(StompEndpointRegistry.class, RETURNS_DEEP_STUBS);
        config.registerStompEndpoints(endpoints);
        verify(endpoints, atLeastOnce()).addEndpoint("/ws");
    }
}
