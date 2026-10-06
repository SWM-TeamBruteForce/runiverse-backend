package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class InvalidEmailFormatException extends BusinessException {

    public InvalidEmailFormatException() {
        super(UserErrorCode.INVALID_EMAIL_FORMAT);
    }
}
