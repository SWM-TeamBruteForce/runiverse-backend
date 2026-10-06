package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class BirthdayRequiredException extends BusinessException {

    public BirthdayRequiredException() {
        super(UserOnboardingErrorCode.BIRTHDAY_REQUIRED);
    }
}
