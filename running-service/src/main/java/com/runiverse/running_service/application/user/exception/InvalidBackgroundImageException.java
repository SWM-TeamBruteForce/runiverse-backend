package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class InvalidBackgroundImageException extends BusinessException {

    public InvalidBackgroundImageException() {
        super(UserErrorCode.INVALID_BACKGROUND_IMAGE);
    }

}
