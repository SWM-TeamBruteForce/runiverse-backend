package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class IntroductionRequiredException extends BusinessException {

    public IntroductionRequiredException() {
        super(UserErrorCode.INTRODUCTION_REQUIRED);
    }
}
