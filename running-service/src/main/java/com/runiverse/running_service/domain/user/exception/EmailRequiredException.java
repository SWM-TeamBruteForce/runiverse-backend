package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class EmailRequiredException extends BusinessException {

    public EmailRequiredException() {
        super(UserErrorCode.EMAIL_REQUIRED);
    }
}
