package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

public class EmailVerificationCooldownException extends BusinessException {

    public EmailVerificationCooldownException() {
        super(AuthErrorCode.EMAIL_VERIFICATION_COOLDOWN);
    }
}
