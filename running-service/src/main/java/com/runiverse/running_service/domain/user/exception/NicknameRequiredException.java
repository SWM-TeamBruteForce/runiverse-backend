package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserOnboardingErrorCode;

public class NicknameRequiredException extends BusinessException {

    public NicknameRequiredException() {
        super(UserOnboardingErrorCode.NICKNAME_REQUIRED);
    }
}
