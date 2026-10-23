package com.runiverse.running_service.unit_test.presentation;

import com.runiverse.running_service.application.app.port.in.CheckAppVersionUsecase;
import com.runiverse.running_service.application.app.query.version.CheckAppVersionQuery;
import com.runiverse.running_service.application.app.query.version.CheckAppVersionResult;
import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.common.filter.AppVersionFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

// 필터에서 끝난 요청은 중앙 예외 핸들러를 거치지 않는다 — 응답 본문·메트릭 원인을 여기서 확인한다
@ExtendWith(MockitoExtension.class)
@DisplayName("앱 버전 검사 필터 단위 테스트")
class AppVersionFilterTest {

    private static final String CONTEXT_PATH = "/api/v1";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Mock
    private CheckAppVersionUsecase checkAppVersionUsecase;

    private AppVersionFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AppVersionFilter(checkAppVersionUsecase, JSON_MAPPER);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    @DisplayName("헤더가 없으면 판정하지 않고 403으로 거절한다")
    void rejectsWithoutHeaders() throws Exception {
        // given -> 헤더를 싣지 않는 구버전 앱(안드로이드 1.0.0)이다
        MockHttpServletRequest request = request("/users/me", null, null);

        // when
        filter.doFilter(request, response, chain);

        // then
        assertUpdateRequired(request);
        verifyNoInteractions(checkAppVersionUsecase);
    }

    @ParameterizedTest(name = "platform={0}, versionName={1}")
    @CsvSource({
            "IOS,",
            ",1.0.0",
            "WEB, 1.0.0",
            "'', 1.0.0",
            "IOS, 1.0",
            "IOS, v1.0.0",
            "IOS, 1.0.0-beta",
            "IOS, 1.0.1000000000",
            "IOS, ' 1.0.0'"
    })
    @DisplayName("헤더가 하나뿐이거나 형식이 틀리면 판정하지 않고 403으로 거절한다")
    void rejectsMalformedHeaders(String platform, String versionName) throws Exception {
        // given -> 통과시키면 아무 값이나 실어 검사를 건너뛸 수 있다
        MockHttpServletRequest request = request("/users/me", platform, versionName);

        // when
        filter.doFilter(request, response, chain);

        // then
        assertUpdateRequired(request);
        verifyNoInteractions(checkAppVersionUsecase);
    }

    @Test
    @DisplayName("최소 버전보다 낮으면 처리하지 않고 403으로 거절한다")
    void rejectsLowerVersion() throws Exception {
        // given
        given(checkAppVersionUsecase.handle(new CheckAppVersionQuery("ANDROID", "1.0.9")))
                .willReturn(new CheckAppVersionResult(true));
        MockHttpServletRequest request = request("/users/me", "ANDROID", "1.0.9");

        // when
        filter.doFilter(request, response, chain);

        // then
        assertUpdateRequired(request);
    }

    @Test
    @DisplayName("최소 버전 이상이면 그대로 처리한다")
    void passesSupportedVersion() throws Exception {
        // given -> 플랫폼 헤더는 대소문자를 가리지 않고 판정으로 넘긴다
        given(checkAppVersionUsecase.handle(new CheckAppVersionQuery("ios", "1.1.0")))
                .willReturn(new CheckAppVersionResult(false));

        // when
        filter.doFilter(request("/users/me", "ios", "1.1.0"), response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/app/version", "/actuator/health", "/actuator/prometheus"})
    @DisplayName("버전 확인 API와 actuator는 구버전이어도 검사하지 않는다")
    void skipsExcludedPaths(String path) throws Exception {
        // when -> 구버전도 업데이트가 필요하다는 답을 받아야 하고, 헬스 체크·수집이 막히면 안 된다
        filter.doFilter(request(path, "ANDROID", "0.0.1"), response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(checkAppVersionUsecase);
    }

    @Test
    @DisplayName("CORS 사전 요청은 앱 버전 헤더가 없어도 검사하지 않는다")
    void skipsCorsPreflight() throws Exception {
        // given -> 브라우저는 사전 요청에 헤더 이름만 싣는다. 막으면 본 요청을 못 보낸다
        MockHttpServletRequest request = request("/auth/login", null, null);
        request.setMethod("OPTIONS");
        request.addHeader("Origin", "http://localhost:3000");
        request.addHeader("Access-Control-Request-Method", "POST");

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(checkAppVersionUsecase);
    }

    @Test
    @DisplayName("사전 요청이 아닌 OPTIONS는 앱 버전 헤더가 없으면 거절한다")
    void rejectsPlainOptionsWithoutHeaders() throws Exception {
        // given -> 빼는 건 CORS 사전 요청뿐이다. 메서드만 OPTIONS라고 검사를 건너뛰면 안 된다
        MockHttpServletRequest request = request("/auth/login", null, null);
        request.setMethod("OPTIONS");

        // when
        filter.doFilter(request, response, chain);

        // then
        assertUpdateRequired(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/app/notices", "/app/version/latest", "/app-versions"})
    @DisplayName("버전 확인 주소와 앞부분만 같은 경로는 검사한다")
    void checksPathsSimilarToExcludedOne(String path) throws Exception {
        // given -> 빼는 건 버전 확인 주소 하나뿐이다. /app 아래 새 API가 검사에서 빠지면 안 된다
        given(checkAppVersionUsecase.handle(new CheckAppVersionQuery("ANDROID", "0.0.1")))
                .willReturn(new CheckAppVersionResult(true));

        // when
        filter.doFilter(request(path, "ANDROID", "0.0.1"), response, chain);

        // then
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("최소 버전을 읽지 못하면 통과시키지 않고 공통 500으로 응답한다")
    void failsClosedWhenLookupFails() throws Exception {
        // given -> 통과시키면 막아야 할 요청이 들어온다
        given(checkAppVersionUsecase.handle(new CheckAppVersionQuery("IOS", "1.0.0")))
                .willThrow(new IllegalStateException("앱 버전 행이 없다 — platform=IOS"));
        MockHttpServletRequest request = request("/users/me", "IOS", "1.0.0");

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "code", "INTERNAL_SERVER_ERROR",
                "message", "서버 오류가 발생했습니다."));
        assertThat(request.getAttribute(HttpRequestMetricsFilter.REASON)).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    // 배포와 같이 컨텍스트 경로 아래로 들어온 요청 — 헤더는 null이면 싣지 않는다
    private static MockHttpServletRequest request(String path, String platform, String versionName) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", CONTEXT_PATH + path);
        request.setContextPath(CONTEXT_PATH);
        if (platform != null) {
            request.addHeader(AppVersionFilter.PLATFORM_HEADER, platform);
        }
        if (versionName != null) {
            request.addHeader(AppVersionFilter.APP_VERSION_HEADER, versionName);
        }
        return request;
    }

    private void assertUpdateRequired(MockHttpServletRequest request) throws Exception {
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "code", "APP_UPDATE_REQUIRED",
                "message", "앱 업데이트가 필요합니다."));
        // 보안 필터처럼 중앙 예외 핸들러를 거치지 않아 원인을 직접 남겨야 UNKNOWN이 되지 않는다
        assertThat(request.getAttribute(HttpRequestMetricsFilter.REASON)).isEqualTo("APP_UPDATE_REQUIRED");
    }

    private Map<String, Object> body() throws Exception {
        return JSON_MAPPER.readValue(response.getContentAsString(StandardCharsets.UTF_8), new TypeReference<>() {
        });
    }
}
