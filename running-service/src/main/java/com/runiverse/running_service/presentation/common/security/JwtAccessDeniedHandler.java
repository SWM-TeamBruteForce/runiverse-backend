package com.runiverse.running_service.presentation.common.security;

import com.runiverse.running_service.observability.metrics.HttpRequestMetricsFilter;
import com.runiverse.running_service.presentation.common.exception.ErrorExposurePolicy;
import com.runiverse.running_service.presentation.common.exception.SecurityErrorCode;
import com.runiverse.running_service.presentation.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        SecurityErrorCode errorCode = SecurityErrorCode.ACCESS_DENIED;
        // 메트릭의 실패 원인 — 보안 필터에서 끝나 중앙 예외 핸들러를 거치지 않는다
        request.setAttribute(HttpRequestMetricsFilter.REASON, errorCode.getCode());
        HttpStatus status = HttpStatus.FORBIDDEN;
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
