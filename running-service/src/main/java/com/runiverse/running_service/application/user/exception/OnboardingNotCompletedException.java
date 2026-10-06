package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class OnboardingNotCompletedException extends BusinessException {

    public OnboardingNotCompletedException() {
        super(UserErrorCode.ONBOARDING_NOT_COMPLETED);
    }
}
