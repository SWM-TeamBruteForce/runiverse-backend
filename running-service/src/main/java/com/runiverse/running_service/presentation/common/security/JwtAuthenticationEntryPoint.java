package com.runiverse.running_service.presentation.common.security;

import com.runiverse.running_service.infrastructure.security.jwt.validator.BlockedTokenValidator;
import com.runiverse.running_service.infrastructure.security.jwt.validator.ExpiredTokenValidator;
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
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        SecurityErrorCode errorCode = resolve(authException);
        // 메트릭의 실패 원인 — 보안 필터에서 끝나 중앙 예외 핸들러를 거치지 않는다
        request.setAttribute(HttpRequestMetricsFilter.REASON, errorCode.getCode());
        log.warn("인증 실패: {} - {}", errorCode.getCode(), errorCode.getMessage());
        HttpStatus status = HttpStatus.UNAUTHORIZED;
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


    private SecurityErrorCode resolve(AuthenticationException authException) {
        // 만료와 블랙리스트는 각 validator가 심어둔 코드로 판별한다
        if (authException.getCause() instanceof JwtValidationException validationException) {
            // 만료
            boolean expired = validationException.getErrors().stream()
                    .anyMatch(error -> ExpiredTokenValidator.ERROR_CODE.equals(error.getErrorCode()));
            if (expired) {
                return SecurityErrorCode.TOKEN_EXPIRED;
            }
            // 블랙리스트
            boolean blocked = validationException.getErrors().stream()
                    .anyMatch(error -> BlockedTokenValidator.ERROR_CODE.equals(error.getErrorCode()));
            if (blocked) {
                return SecurityErrorCode.TOKEN_BLOCKED;
            }
        }
        // 토큰을 아예 보내지 않은 경우
        if (authException instanceof InsufficientAuthenticationException) {
            return SecurityErrorCode.AUTHENTICATION_REQUIRED;
        }
        return SecurityErrorCode.INVALID_TOKEN;
    }
}
