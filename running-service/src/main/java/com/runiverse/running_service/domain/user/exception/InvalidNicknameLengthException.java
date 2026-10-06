package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class InvalidNicknameLengthException extends BusinessException {

    public InvalidNicknameLengthException() {
        super(UserOnboardingErrorCode.INVALID_NICKNAME_LENGTH);
    }
}
