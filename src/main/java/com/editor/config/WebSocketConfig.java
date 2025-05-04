package com.editor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {



    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for topics and user queues
        config.enableSimpleBroker("/topic", "/queue");

        // Set prefix for application destinations
        config.setApplicationDestinationPrefixes("/app");

        // Set prefix for user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoint - THIS IS THE CRITICAL PART
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Allow all origins for testing
                .withSockJS();  // Enable SockJS fallback

        // ALSO add a direct WebSocket endpoint without SockJS for JavaFX clients
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}