package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.OauthUserErrorCode;

public class ProviderIdTooLongException extends BusinessException {

    public ProviderIdTooLongException() {
        super(OauthUserErrorCode.PROVIDER_ID_TOO_LONG);
    }
}
