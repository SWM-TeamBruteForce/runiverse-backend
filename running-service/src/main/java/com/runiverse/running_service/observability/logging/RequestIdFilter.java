package com.runiverse.running_service.observability.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// 요청마다 추적용 ID를 발급해 그 요청의 모든 로그에 붙인다
// 보안 필터보다 먼저 실행해 인증 실패 로그에도 ID가 남게 한다
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        MDC.put(REQUEST_ID, UUID.randomUUID().toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 톰캣 스레드는 재사용되므로 지우지 않으면 다음 요청에 ID가 섞인다
            MDC.remove(REQUEST_ID);
        }
    }
}
