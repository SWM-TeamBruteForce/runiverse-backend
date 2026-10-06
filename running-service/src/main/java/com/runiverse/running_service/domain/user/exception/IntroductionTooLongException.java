package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class IntroductionTooLongException extends BusinessException {

    public IntroductionTooLongException() {
        super(UserErrorCode.INTRODUCTION_TOO_LONG);
    }
}
