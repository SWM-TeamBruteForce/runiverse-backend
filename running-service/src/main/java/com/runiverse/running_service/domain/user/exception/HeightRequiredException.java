package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class HeightRequiredException extends BusinessException {

    public HeightRequiredException() {
        super(UserOnboardingErrorCode.HEIGHT_REQUIRED);
    }
}
