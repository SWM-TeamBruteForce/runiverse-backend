package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.OauthUserErrorCode;

public class ProviderIdRequiredException extends BusinessException {

    public ProviderIdRequiredException() {
        super(OauthUserErrorCode.PROVIDER_ID_REQUIRED);
    }
}
