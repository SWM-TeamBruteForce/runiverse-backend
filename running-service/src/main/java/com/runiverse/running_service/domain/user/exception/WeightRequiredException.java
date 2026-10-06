package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class WeightRequiredException extends BusinessException {

    public WeightRequiredException() {
        super(UserOnboardingErrorCode.WEIGHT_REQUIRED);
    }
}
