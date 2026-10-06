package com.runiverse.running_service.observability;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

// 요청을 처리한 컨트롤러의 패키지로 기능 도메인을 정한다 — 로그 태그와 메트릭 domain이 같은 기준을 쓴다
// 레이어를 import하지 않도록 패키지 이름 문자열로만 판단한다
public final class RequestDomain {

    public static final String COMMON = "common";
    private static final String PRESENTATION_PACKAGE = "com.runiverse.running_service.presentation.";
    private static final Set<String> DOMAINS = Set.of("auth", "user", "match", "running");

    private RequestDomain() {
    }

    // 컨트롤러에 닿기 전에 끝난 요청(없는 경로, 보안 필터 거절)은 처리한 컨트롤러가 없어 common이다
    public static String of(HttpServletRequest request) {
        if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
                instanceof HandlerMethod handlerMethod)) {
            return COMMON;
        }
        String packageName = handlerMethod.getBeanType().getPackageName();
        if (!packageName.startsWith(PRESENTATION_PACKAGE)) {
            return COMMON;
        }
        String domain = packageName.substring(PRESENTATION_PACKAGE.length()).split("\\.")[0];
        return DOMAINS.contains(domain) ? domain : COMMON;
    }
}
