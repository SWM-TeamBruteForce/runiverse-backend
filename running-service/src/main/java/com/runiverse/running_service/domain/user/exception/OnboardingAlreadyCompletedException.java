package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class OnboardingAlreadyCompletedException extends BusinessException {

    public OnboardingAlreadyCompletedException() {
        super(UserOnboardingErrorCode.ONBOARDING_ALREADY_COMPLETED);
    }
}
