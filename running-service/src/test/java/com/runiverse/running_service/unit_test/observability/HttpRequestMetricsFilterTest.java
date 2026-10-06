package com.runiverse.running_service.unit_test.observability;

import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HTTP 요청 메트릭 필터 단위 테스트")
class HttpRequestMetricsFilterTest {

    private static final String NAME = "runiverse.http.requests";

    private SimpleMeterRegistry meterRegistry;
    private HttpRequestMetricsFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new HttpRequestMetricsFilter(meterRegistry);
    }

    // 컨트롤러까지 간 요청처럼 매칭된 템플릿과 응답 상태를 남기는 체인
    private static FilterChain handled(String pattern, int status, String reason) {
        return (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
            if (reason != null) {
                req.setAttribute(HttpRequestMetricsFilter.REASON, reason);
            }
            ((MockHttpServletResponse) res).setStatus(status);
        };
    }

    private Counter counter(String... tags) {
        return meterRegistry.find(NAME).tags(tags).counter();
    }

    @Test
    @DisplayName("성공한 요청은 매칭된 템플릿과 reason=none으로 센다")
    void countsSuccessWithTemplate() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/0192f3a4-0000-7000-8000-000000000000");

        // when
        filter.doFilter(request, new MockHttpServletResponse(), handled("/users/{userId}", 200, null));

        // then -> 원문 경로를 쓰면 사용자 수만큼 시계열이 생긴다
        Counter counter = counter("domain", "common", "method", "GET", "uri", "/users/{userId}",
                "result", "success", "reason", "none");
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패한 요청은 예외를 처리한 쪽이 남긴 원인으로 센다")
    void countsFailureWithMarkedReason() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/signup");

        // when
        filter.doFilter(request, new MockHttpServletResponse(),
                handled("/auth/signup", 409, "EMAIL_ALREADY_EXISTS"));

        // then
        assertThat(counter("result", "failure", "reason", "EMAIL_ALREADY_EXISTS", "uri", "/auth/signup"))
                .isNotNull();
    }

    @Test
    @DisplayName("원인이 남지 않은 실패는 UNKNOWN으로 센다")
    void countsUnmarkedFailureAsUnknown() throws Exception {
        // given -> 원인을 남기지 않는 예외 경로가 생겼다는 신호다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/somewhere");

        // when
        filter.doFilter(request, new MockHttpServletResponse(), handled("/somewhere", 500, null));

        // then
        assertThat(counter("result", "failure", "reason", "UNKNOWN")).isNotNull();
    }

    @Test
    @DisplayName("컨트롤러에 닿기 전에 끝난 요청은 uri=UNKNOWN, domain=common으로 센다")
    void countsRejectedBeforeDispatchAsUnknownUri() throws Exception {
        // given -> 보안 필터가 거절하면 경로 템플릿이 정해지지 않는다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        FilterChain rejected = (req, res) -> {
            req.setAttribute(HttpRequestMetricsFilter.REASON, "AUTHENTICATION_REQUIRED");
            ((MockHttpServletResponse) res).setStatus(401);
        };

        // when
        filter.doFilter(request, new MockHttpServletResponse(), rejected);

        // then
        assertThat(counter("domain", "common", "uri", "UNKNOWN",
                "result", "failure", "reason", "AUTHENTICATION_REQUIRED")).isNotNull();
    }

    @Test
    @DisplayName("필터 밖으로 샌 예외는 INTERNAL_SERVER_ERROR로 세고 그대로 던진다")
    void countsEscapedExceptionAndRethrows() {
        // given -> 중앙 예외 핸들러를 거치지 못하면 컨테이너가 500으로 응답한다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boom");
        FilterChain exploding = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        // when & then
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), exploding))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counter("result", "failure", "reason", "INTERNAL_SERVER_ERROR")).isNotNull();
    }

    @Test
    @DisplayName("목록 밖의 HTTP 메서드는 OTHER로 묶는다")
    void unknownMethodIsOther() throws Exception {
        // given -> 클라이언트는 임의의 메서드 이름을 보낼 수 있다
        MockHttpServletRequest request = new MockHttpServletRequest("BREW", "/coffee");

        // when
        filter.doFilter(request, new MockHttpServletResponse(), handled("/coffee", 405, "INVALID_REQUEST"));

        // then
        assertThat(counter("method", "OTHER")).isNotNull();
    }

    @Test
    @DisplayName("actuator 요청은 세지 않는다")
    void skipsActuator() throws Exception {
        // given -> Prometheus가 주기적으로 긁는 요청까지 세면 트래픽이 부풀어 보인다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/actuator/prometheus");
        request.setContextPath("/api/v1");

        // when
        filter.doFilter(request, new MockHttpServletResponse(), handled("/actuator/prometheus", 200, null));

        // then
        assertThat(meterRegistry.find(NAME).counters()).isEmpty();
    }
}
