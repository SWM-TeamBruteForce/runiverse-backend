package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

public class EmailVerificationDailyLimitExceededException extends BusinessException {

    public EmailVerificationDailyLimitExceededException() {
        super(AuthErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED);
    }
}
