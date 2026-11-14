package com.runiverse.running_service.unit_test.match.presentation;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.exception.ActiveMatchNotFoundException;
import com.runiverse.running_service.application.match.exception.MatchStreamUnavailableException;
import com.runiverse.running_service.application.match.port.in.CloseMatchStreamUsecase;
import com.runiverse.running_service.application.match.port.in.OpenMatchStreamUsecase;
import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.match.controller.MatchStreamController;
import com.runiverse.running_service.presentation.match.sse.MatchStreamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

// SSE 응답은 MockMvc 비동기 처리가 필요해 컨트롤러를 직접 부른다 — 열지 못한 연결의 응답 상태와 메트릭 원인만 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("매칭 스트림 컨트롤러 단위 테스트")
class MatchStreamControllerTest {

    // UserId VO가 UUIDv7만 받는다
    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();

    @Mock
    private OpenMatchStreamUsecase openMatchStreamUsecase;

    @Mock
    private CloseMatchStreamUsecase closeMatchStreamUsecase;

    private MatchStreamController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new MatchStreamController(
                openMatchStreamUsecase, closeMatchStreamUsecase, new MatchStreamProperties(Duration.ofMinutes(30)));
        request = new MockHttpServletRequest();
    }

    @Test
    @DisplayName("방 채널 구독에 실패하면 본문 없는 503이고 원인을 메트릭에 남긴다")
    void streamUnavailableIsServiceUnavailable() throws Exception {
        // given
        willThrow(new MatchStreamUnavailableException()).given(openMatchStreamUsecase).handle(any());

        // when
        ResponseEntity<SseEmitter> response = controller.stream(jwt(), request);

        // then -> 중앙 핸들러를 거치지 않아 여기서 남기지 않으면 원인이 UNKNOWN으로 찍힌다
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNull();
        assertThat(request.getAttribute(HttpRequestMetricsFilter.REASON)).isEqualTo("MATCH_STREAM_UNAVAILABLE");
        verifyNoInteractions(closeMatchStreamUsecase);
    }

    @Test
    @DisplayName("활성 신청이 없으면 본문 없는 404이고 원인을 메트릭에 남긴다")
    void noActiveMatchIsNotFound() throws Exception {
        // given
        willThrow(new ActiveMatchNotFoundException()).given(openMatchStreamUsecase).handle(any());

        // when
        ResponseEntity<SseEmitter> response = controller.stream(jwt(), request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNull();
        assertThat(request.getAttribute(HttpRequestMetricsFilter.REASON)).isEqualTo("NOT_FOUND");
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .build();
    }
}
