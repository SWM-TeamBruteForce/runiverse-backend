package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class InvalidBirthdayException extends BusinessException {

    public InvalidBirthdayException() {
        super(UserOnboardingErrorCode.INVALID_BIRTHDAY);
    }
}
