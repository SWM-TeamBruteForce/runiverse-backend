package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

public class InvalidVerificationCodeException extends BusinessException {

    public InvalidVerificationCodeException() {
        super(AuthErrorCode.INVALID_VERIFICATION_CODE);
    }
}
