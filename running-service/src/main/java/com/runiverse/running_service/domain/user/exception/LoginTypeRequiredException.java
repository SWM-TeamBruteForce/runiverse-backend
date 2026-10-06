package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.DeletedUserErrorCode;

public class LoginTypeRequiredException extends BusinessException {

    public LoginTypeRequiredException() {
        super(DeletedUserErrorCode.LOGIN_TYPE_REQUIRED);
    }
}
