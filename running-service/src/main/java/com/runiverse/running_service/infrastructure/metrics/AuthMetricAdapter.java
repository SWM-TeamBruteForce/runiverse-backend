package com.runiverse.running_service.infrastructure.metrics;

import com.runiverse.running_service.application.auth.port.out.RecordAuthMetricPort;
import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.domain.user.vo.Provider;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AuthMetricAdapter implements RecordAuthMetricPort {

    private static final String OAUTH_LOGIN = "runiverse.auth.oauthlogin";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    // 같은 이름의 메트릭은 태그 key가 같아야 한다 — 성공에도 reason을 채운다
    private static final String NO_REASON = "none";
    // 요청이 보낸 provider 문자열을 그대로 쓰면 태그 값이 무한히 늘어난다
    private static final String UNKNOWN_PROVIDER = "unknown";
    private final MeterRegistry meterRegistry;

    @Override
    public void oauthLoginSucceeded(Provider provider) {
        countOauthLogin(provider, SUCCESS, NO_REASON);
    }

    @Override
    public void oauthLoginFailed(Provider provider, ErrorCode errorCode) {
        countOauthLogin(provider, FAILURE, errorCode.getCode());
    }

    private void countOauthLogin(Provider provider, String result, String reason) {
        String providerTag = provider == null
                ? UNKNOWN_PROVIDER
                : provider.name().toLowerCase(Locale.ROOT);
        meterRegistry.counter(OAUTH_LOGIN,
                "provider", providerTag,
                "result", result,
                "reason", reason
        ).increment();
    }
}
