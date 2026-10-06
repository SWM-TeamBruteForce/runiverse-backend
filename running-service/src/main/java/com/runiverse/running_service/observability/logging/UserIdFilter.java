package com.runiverse.running_service.observability.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 인증된 요청이면 access token의 userId를 그 요청의 모든 로그에 붙인다
// 인증 결과를 읽어야 해서 보안 필터 체인 뒤에 실행한다
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class UserIdFilter extends OncePerRequestFilter {

    public static final String USER_ID = "userId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 공개 경로는 익명 인증이 들어 있다 — access token으로 인증된 요청만 붙인다
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        MDC.put(USER_ID, jwtAuthentication.getName());
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 톰캣 스레드는 재사용되므로 지우지 않으면 다음 요청에 userId가 섞인다
            MDC.remove(USER_ID);
        }
    }
}
