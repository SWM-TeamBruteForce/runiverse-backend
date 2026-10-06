package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class AvgPaceOutOfRangeException extends BusinessException {

    public AvgPaceOutOfRangeException() {
        super(UserOnboardingErrorCode.AVG_PACE_OUT_OF_RANGE);
    }
}
