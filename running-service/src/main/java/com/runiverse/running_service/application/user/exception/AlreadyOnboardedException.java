package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class AlreadyOnboardedException extends BusinessException {

    public AlreadyOnboardedException() {
        super(UserErrorCode.ALREADY_ONBOARDED);
    }
}
