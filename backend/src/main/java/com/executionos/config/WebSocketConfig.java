package com.executionos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;

    public WebSocketConfig(@Value("${executionos.cors-origins}") String corsOrigins) {
        this.allowedOrigins = corsOrigins.split(",");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Reuses the same origin allow-list as the REST API's CORS config
        // (executionos.cors-origins) instead of "*", so a WebSocket
        // handshake can't be initiated from an arbitrary third-party page.
        registry.addEndpoint("/ws/notifications").setAllowedOrigins(allowedOrigins);
    }
}
