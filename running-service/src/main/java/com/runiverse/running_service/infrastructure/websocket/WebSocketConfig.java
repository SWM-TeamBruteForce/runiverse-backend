package com.runiverse.running_service.infrastructure.websocket;

import com.runiverse.running_service.infrastructure.security.CorsProperties;
import com.runiverse.running_service.presentation.common.security.JwtHandshakeInterceptor;
import com.runiverse.running_service.presentation.running.websocket.RunningWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;


@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final RunningWebSocketHandler runningWebsocketHandler;
    private final CorsProperties corsProperties;
    private final WebSocketProperties webSocketProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(runningWebsocketHandler, webSocketProperties.runningEndpoint())
                .addInterceptors(new JwtHandshakeInterceptor())
                // 핸드셰이크 Origin 검사는 CORS 필터와 별개다(기본값 SAME_ORIGIN).
                // 신뢰 출처의 정본은 하나여야 하므로 cors.allowed-origins를 그대로 쓴다.
                // 모바일 앱은 Origin 헤더를 보내지 않아 이 검사에 걸리지 않는다.
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    // 웹소켓 연결의 전체 적인 부분을 관리하는 container이다 여기서 session등을 관리할 수 있다.
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize((int) webSocketProperties.maxTextMessageBufferSize().toBytes());
        container.setMaxSessionIdleTimeout(webSocketProperties.idleTimeout().toMillis());
        return container;
    }
}
