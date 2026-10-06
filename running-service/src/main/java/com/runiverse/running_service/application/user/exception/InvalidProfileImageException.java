package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class InvalidProfileImageException extends BusinessException {

    public InvalidProfileImageException() {
        super(UserErrorCode.INVALID_PROFILE_IMAGE);
    }

}
