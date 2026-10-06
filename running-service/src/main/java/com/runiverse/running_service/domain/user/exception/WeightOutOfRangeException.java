package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class WeightOutOfRangeException extends BusinessException {

    public WeightOutOfRangeException() {
        super(UserOnboardingErrorCode.WEIGHT_OUT_OF_RANGE);
    }
}
