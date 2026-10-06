package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class InvalidNicknameFormatException extends BusinessException {

    public InvalidNicknameFormatException() {
        super(UserOnboardingErrorCode.INVALID_NICKNAME_FORMAT);
    }
}
