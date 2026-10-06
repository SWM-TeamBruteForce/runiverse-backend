package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

// 제공자 장애·점검·호출 한도 초과·연결 실패 — 사용자 자격 증명 문제가 아니라 잠시 후 다시 시도할 일이다
public class OauthProviderUnavailableException extends BusinessException {

    public OauthProviderUnavailableException() {
        super(AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
    }
}
