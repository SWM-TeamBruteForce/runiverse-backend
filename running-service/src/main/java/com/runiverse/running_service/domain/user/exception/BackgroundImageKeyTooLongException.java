package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class BackgroundImageKeyTooLongException extends BusinessException {

    public BackgroundImageKeyTooLongException() {
        super(UserErrorCode.BACKGROUND_IMAGE_KEY_TOO_LONG);
    }

}
