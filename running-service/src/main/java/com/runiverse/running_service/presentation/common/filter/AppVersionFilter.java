package com.runiverse.running_service.presentation.common.filter;

import com.runiverse.running_service.application.app.port.in.CheckAppVersionUsecase;
import com.runiverse.running_service.application.app.query.version.CheckAppVersionQuery;
import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.common.exception.CommonErrorCode;
import com.runiverse.running_service.presentation.common.exception.ErrorExposurePolicy;
import com.runiverse.running_service.presentation.common.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

// 버전을 알 수 없거나 그 플랫폼의 최소 버전보다 낮은 앱의 요청을 처리하기 전에 거절한다 —
// 호환을 깨는 배포 뒤 이미 떠 있던 구버전 앱의 요청이 기록·상태를 틀어지게 하지 않기 위해서다.
// 서버 등록과 실행 순서는 AppVersionFilterConfig가 정한다
@Slf4j
@Component
@RequiredArgsConstructor
public class AppVersionFilter extends OncePerRequestFilter {

    public static final String PLATFORM_HEADER = "X-App-Platform";
    public static final String APP_VERSION_HEADER = "X-App-Version";

    private static final Pattern PLATFORM_FORMAT = Pattern.compile("^(?i)(IOS|ANDROID)$");
    // VersionName과 같은 규칙 — 부분마다 9자리까지
    private static final Pattern VERSION_NAME_FORMAT =
            Pattern.compile("^[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}$");
    // 구버전도 업데이트가 필요하다는 답을 받아야 한다 — 이 주소 하나만 뺀다. /app 아래 다른 API는 검사한다
    private static final String APP_VERSION_PATH = "/app/version";
    private static final String ACTUATOR_PATH = "/actuator/";

    private final CheckAppVersionUsecase checkAppVersionUsecase;
    private final ObjectMapper objectMapper;

    // CORS 사전 요청은 브라우저가 헤더 이름만 싣고 처리도 하지 않는다 — 막으면 본 요청을 못 보낸다.
    // 판정은 뒤의 CORS 설정에 맡기고, 본 요청은 여기서 검사한다
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return uri.equals(contextPath + APP_VERSION_PATH) || uri.startsWith(contextPath + ACTUATOR_PATH)
                || CorsUtils.isPreFlightRequest(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String platform = request.getHeader(PLATFORM_HEADER);
        String versionName = request.getHeader(APP_VERSION_HEADER);
        // 헤더가 없거나 틀리면 최소 버전 미만과 같이 거절한다 — 헤더를 싣지 않는 구버전 앱을 막는다.
        // 통과시키면 아무 값이나 실어 검사를 건너뛸 수 있다
        if (!isWellFormed(platform, versionName)) {
            reject(request, response, HttpStatus.FORBIDDEN, CommonErrorCode.APP_UPDATE_REQUIRED);
            return;
        }

        boolean updateRequired;
        try {
            updateRequired = checkAppVersionUsecase.handle(new CheckAppVersionQuery(platform, versionName))
                    .updateRequired();
        } catch (RuntimeException e) {
            // 최소 버전을 모르면 통과시키지 않는다 — 막아야 할 요청이 들어온다.
            // 필터의 예외는 중앙 예외 핸들러가 받지 않아 여기서 공통 500으로 응답한다
            log.error("[앱] 버전 확인 실패: 최소 버전 조회 오류 - platform={}", platform, e);
            reject(request, response, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_SERVER_ERROR);
            return;
        }
        if (updateRequired) {
            reject(request, response, HttpStatus.FORBIDDEN, CommonErrorCode.APP_UPDATE_REQUIRED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isWellFormed(String platform, String versionName) {
        return platform != null && versionName != null
                && PLATFORM_FORMAT.matcher(platform).matches() && VERSION_NAME_FORMAT.matcher(versionName).matches();
    }

    // 중앙 예외 핸들러를 거치지 않으므로 메트릭 원인·노출 정책을 여기서 맞춘다
    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            CommonErrorCode errorCode
    ) throws IOException {
        request.setAttribute(HttpRequestMetricsFilter.REASON, errorCode.getCode());
        ErrorResponse body = new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
        if (!ErrorExposurePolicy.isExposed(status, errorCode.getCode())) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            body = ErrorExposurePolicy.masked();
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
