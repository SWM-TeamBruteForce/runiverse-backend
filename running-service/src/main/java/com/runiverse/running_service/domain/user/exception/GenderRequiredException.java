package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class GenderRequiredException extends BusinessException {

    public GenderRequiredException() {
        super(UserOnboardingErrorCode.GENDER_REQUIRED);
    }
}
