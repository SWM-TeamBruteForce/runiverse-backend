package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class InvalidPasswordHashFormatException extends BusinessException {

    public InvalidPasswordHashFormatException() {
        super(UserErrorCode.INVALID_PASSWORD_HASH_FORMAT);
    }
}
