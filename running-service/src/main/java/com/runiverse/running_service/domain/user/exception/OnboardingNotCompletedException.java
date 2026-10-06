package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class OnboardingNotCompletedException extends BusinessException {

    public OnboardingNotCompletedException() {
        super(UserOnboardingErrorCode.ONBOARDING_NOT_COMPLETED);
    }
}
