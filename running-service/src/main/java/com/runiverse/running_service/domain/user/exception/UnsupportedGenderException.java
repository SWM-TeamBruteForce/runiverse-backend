package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class UnsupportedGenderException extends BusinessException {

    public UnsupportedGenderException() {
        super(UserOnboardingErrorCode.UNSUPPORTED_GENDER);
    }
}
