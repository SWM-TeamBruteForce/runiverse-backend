package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

public class UnsupportedProviderException extends BusinessException {

    public UnsupportedProviderException() {
        super(AuthErrorCode.UNSUPPORTED_PROVIDER);
    }
}
