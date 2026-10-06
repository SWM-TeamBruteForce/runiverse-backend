package com.runiverse.running_service.observability.metrics;

import com.runiverse.running_service.observability.RequestDomain;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Set;

// 모든 HTTP 요청을 runiverse.http.requests로 센다 — 기본 제공 메트릭에 없는 domain·reason을 붙이려고 따로 센다
// 보안 필터보다 바깥에서 감싸야 인증 실패(401)도 센다
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class HttpRequestMetricsFilter extends OncePerRequestFilter {

    // 예외를 처리한 쪽(중앙 예외 핸들러·인증 진입점)이 실패 원인을 남기는 요청 속성
    public static final String REASON = HttpRequestMetricsFilter.class.getName() + ".reason";

    private static final String NAME = "runiverse.http.requests";
    private static final String ACTUATOR_PATH = "/actuator/";
    private static final String UNKNOWN_URI = "UNKNOWN";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String NO_REASON = "none";
    // 실패했는데 원인이 남지 않았다 — 원인을 남기지 않는 새 예외 경로가 생겼다는 신호다
    private static final String UNKNOWN_REASON = "UNKNOWN";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    private static final int FIRST_ERROR_STATUS = 400;
    // 클라이언트는 임의의 메서드 이름을 보낼 수 있다 — 목록 밖은 하나로 묶어 태그 값이 늘지 않게 한다
    private static final Set<String> METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final String OTHER_METHOD = "OTHER";

    private final MeterRegistry meterRegistry;

    // Prometheus가 주기적으로 긁는 요청까지 세면 트래픽이 실제보다 부풀어 보인다
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + ACTUATOR_PATH);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            // 중앙 예외 핸들러까지 가지 못하고 필터 밖으로 샌 예외 — 컨테이너가 500으로 응답한다
            count(request, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
            throw e;
        }
        count(request, response.getStatus(), (String) request.getAttribute(REASON));
    }

    private void count(HttpServletRequest request, int status, String reason) {
        boolean failed = status >= FIRST_ERROR_STATUS;
        meterRegistry.counter(NAME,
                "domain", RequestDomain.of(request),
                "method", method(request),
                "uri", uri(request),
                "result", failed ? FAILURE : SUCCESS,
                "reason", failed ? (reason == null ? UNKNOWN_REASON : reason) : NO_REASON
        ).increment();
    }

    private static String method(HttpServletRequest request) {
        String method = request.getMethod();
        return METHODS.contains(method) ? method : OTHER_METHOD;
    }

    // 원문 경로를 쓰면 /users/0192... 처럼 값이 무한히 늘어난다 — 매칭된 템플릿만 쓴다
    private static String uri(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? UNKNOWN_URI : pattern.toString();
    }
}
