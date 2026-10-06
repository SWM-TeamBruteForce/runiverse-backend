package com.runiverse.running_service.unit_test.presentation;

import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.common.security.JwtAccessDeniedHandler;
import com.runiverse.running_service.presentation.common.security.JwtAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// 보안 필터에서 끝난 요청은 중앙 예외 핸들러를 거치지 않는다 — 원인을 여기서 남겨야 UNKNOWN이 되지 않는다
@DisplayName("보안 거절의 메트릭 실패 원인 단위 테스트")
class SecurityHandlerMetricsReasonTest {

    @Test
    @DisplayName("토큰 없이 들어온 요청은 AUTHENTICATION_REQUIRED를 원인으로 남긴다")
    void authenticationEntryPointMarksReason() throws Exception {
        // given
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(JsonMapper.builder().build());
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        entryPoint.commence(request, new MockHttpServletResponse(),
                new InsufficientAuthenticationException("no token"));

        // then
        assertThat(request.getAttribute(HttpRequestMetricsFilter.REASON)).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("권한이 없는 요청은 ACCESS_DENIED를 원인으로 남긴다")
    void accessDeniedHandlerMarksReason() throws Exception {
        // given
        JwtAccessDeniedHandler handler = new JwtAccessDeniedHandler(JsonMapper.builder().build());
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        handler.handle(request, new MockHttpServletResponse(), new AccessDeniedException("denied"));

        // then
        assertThat(request.getAttribute(HttpRequestMetricsFilter.REASON)).isEqualTo("ACCESS_DENIED");
    }
}
