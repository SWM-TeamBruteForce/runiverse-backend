package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

public class InvalidRefreshTokenException extends BusinessException {

    public InvalidRefreshTokenException() {
        super(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    ;
}
