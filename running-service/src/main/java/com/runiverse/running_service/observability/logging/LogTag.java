package com.runiverse.running_service.observability.logging;

import com.runiverse.running_service.observability.RequestDomain;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

// 로그 앞머리의 [도메인] 태그 — 도메인 판별은 메트릭과 같은 RequestDomain을 쓴다
public final class LogTag {

    private static final Map<String, String> TAGS = Map.of(
            "auth", "[인증]",
            "user", "[회원]",
            "match", "[매칭]",
            "running", "[러닝]",
            RequestDomain.COMMON, "[공통]"
    );

    private LogTag() {
    }

    public static String of(HttpServletRequest request) {
        return TAGS.get(RequestDomain.of(request));
    }
}
