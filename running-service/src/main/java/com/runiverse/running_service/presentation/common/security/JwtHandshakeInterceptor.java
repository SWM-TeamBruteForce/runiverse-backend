package com.runiverse.running_service.presentation.common.security;

import com.runiverse.running_service.domain.common.vo.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID = "userId";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (!(SecurityContextHolder.getContext().getAuthentication()
                instanceof JwtAuthenticationToken authentication
        )) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ID, new UserId(UUID.fromString(authentication.getToken().getSubject())));
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {

    }
}
