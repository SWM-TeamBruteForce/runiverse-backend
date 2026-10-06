package com.runiverse.running_service.unit_test.presentation;

import com.runiverse.running_service.application.auth.exception.EmailAlreadyExistsException;
import com.runiverse.running_service.application.match.exception.MatchCooldownException;
import com.runiverse.running_service.application.user.exception.UserNotFoundException;
import com.runiverse.running_service.domain.user.vo.Email;
import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.common.exception.GlobalExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 예외 핸들러가 메서드마다 원인을 정확히 하나씩 남기는지 필터까지 이어 붙여 확인한다.
// 핸들러에 원인을 빠뜨리면 UNKNOWN으로, 한 핸들러에 여러 번 남기면 엉뚱한 원인으로 세어진다
@DisplayName("예외 핸들러의 메트릭 실패 원인 단위 테스트")
class GlobalExceptionHandlerMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new HttpRequestMetricsFilter(meterRegistry))
                .build();
    }

    private void perform(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request);
    }

    // 한 요청만 보냈으므로 시계열이 정확히 하나여야 한다 — 그 태그를 돌려준다
    private String only(String tag) {
        assertThat(meterRegistry.find("runiverse.http.requests").counters()).hasSize(1);
        return meterRegistry.find("runiverse.http.requests").counter().getId().getTag(tag);
    }

    @Test
    @DisplayName("성공한 요청은 reason=none이고 uri는 매칭된 템플릿이다")
    void success() throws Exception {
        perform(get("/probe/users/{userId}", UUID.randomUUID()));

        assertThat(only("result")).isEqualTo("success");
        assertThat(only("reason")).isEqualTo("none");
        assertThat(only("uri")).isEqualTo("/probe/users/{userId}");
    }

    @Test
    @DisplayName("유스케이스 예외는 ErrorCode를 원인으로 남긴다")
    void businessException() throws Exception {
        perform(post("/probe/business"));

        assertThat(only("reason")).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("응답에서 500으로 숨기는 코드도 실제 코드를 원인으로 남긴다")
    void maskedBusinessException() throws Exception {
        perform(post("/probe/masked"));

        assertThat(only("reason")).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("도메인 검증 예외는 도메인 코드를 원인으로 남긴다")
    void domainException() throws Exception {
        perform(post("/probe/domain"));

        assertThat(only("reason")).isEqualTo("INVALID_EMAIL_FORMAT");
    }

    @Test
    @DisplayName("요청 검증 실패는 INVALID_REQUEST를 원인으로 남긴다")
    void validationFailure() throws Exception {
        perform(post("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"));

        assertThat(only("reason")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("본문을 읽지 못하면 MALFORMED_REQUEST_BODY를 원인으로 남긴다")
    void malformedBody() throws Exception {
        perform(post("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("{ 깨진"));

        assertThat(only("reason")).isEqualTo("MALFORMED_REQUEST_BODY");
    }

    @Test
    @DisplayName("경로 변수 변환 실패는 INVALID_REQUEST를 원인으로 남긴다")
    void typeMismatch() throws Exception {
        perform(get("/probe/users/{userId}", "not-a-uuid"));

        assertThat(only("reason")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("없는 경로는 500이 아니라 404로 응답하고 NOT_FOUND를 원인으로 남긴다")
    void noResourceFound() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/probe/no-resource")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("\"code\":\"NOT_FOUND\"");
        assertThat(only("reason")).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("예상 못 한 예외는 INTERNAL_SERVER_ERROR를 원인으로 남긴다")
    void unexpectedException() throws Exception {
        perform(post("/probe/unexpected"));

        assertThat(only("reason")).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("쿨다운 예외는 전용 핸들러에서도 원인을 남긴다")
    void cooldown() throws Exception {
        perform(post("/probe/cooldown"));

        assertThat(only("reason")).isEqualTo("MATCH_COOLDOWN");
    }

    record ProbeRequest(@NotBlank String name) {
    }

    // 예외 핸들러마다 하나씩 닿는 경로
    @RestController
    static class ProbeController {

        @GetMapping("/probe/users/{userId}")
        String user(@PathVariable UUID userId) {
            return "ok";
        }

        @PostMapping("/probe/business")
        void business() {
            throw new EmailAlreadyExistsException();
        }

        @PostMapping("/probe/masked")
        void masked() {
            throw new UserNotFoundException();
        }

        @PostMapping("/probe/domain")
        void domain() {
            new Email("not-an-email");
        }

        @PostMapping("/probe/validated")
        void validated(@Valid @RequestBody ProbeRequest request) {
        }

        // 실제 앱에서는 매핑이 없으면 정적 리소스 핸들러가 이 예외를 던진다 — standalone MockMvc에는 그 핸들러가 없어 직접 던진다
        @GetMapping("/probe/no-resource")
        void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/probe/no-resource", "probe/no-resource");
        }

        @PostMapping("/probe/unexpected")
        void unexpected() {
            throw new IllegalStateException("boom");
        }

        @PostMapping("/probe/cooldown")
        void cooldown() {
            throw new MatchCooldownException(LocalDateTime.now());
        }
    }
}
