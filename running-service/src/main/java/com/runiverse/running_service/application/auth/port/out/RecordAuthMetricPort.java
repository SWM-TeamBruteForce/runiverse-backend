package com.runiverse.running_service.application.auth.port.out;

import com.runiverse.running_service.application.common.exception.ErrorCode;
import com.runiverse.running_service.domain.user.vo.Provider;

// 인증 유스케이스 안에서만 보이는 결과를 센다 — 메트릭 이름·태그는 구현체가 정한다
public interface RecordAuthMetricPort {

    void oauthLoginSucceeded(Provider provider);

    // 지원하지 않는 provider면 provider가 null이다
    void oauthLoginFailed(Provider provider, ErrorCode errorCode);
}
