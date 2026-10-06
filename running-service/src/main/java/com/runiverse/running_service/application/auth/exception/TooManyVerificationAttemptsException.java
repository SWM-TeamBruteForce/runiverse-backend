package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

public class TooManyVerificationAttemptsException extends BusinessException {

    public TooManyVerificationAttemptsException() {
        super(AuthErrorCode.TOO_MANY_VERIFICATION_ATTEMPTS);
    }
}
