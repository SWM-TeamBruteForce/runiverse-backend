package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class PasswordHashRequiredException extends BusinessException {

    public PasswordHashRequiredException() {
        super(UserErrorCode.PASSWORD_HASH_REQUIRED);
    }
}
