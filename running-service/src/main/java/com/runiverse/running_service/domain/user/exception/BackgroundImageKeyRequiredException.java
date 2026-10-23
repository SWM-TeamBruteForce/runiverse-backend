package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class BackgroundImageKeyRequiredException extends BusinessException {

    public BackgroundImageKeyRequiredException() {
        super(UserErrorCode.BACKGROUND_IMAGE_KEY_REQUIRED);
    }

}
