package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class BackgroundImageNotUploadedException extends BusinessException {

    public BackgroundImageNotUploadedException() {
        super(UserErrorCode.BACKGROUND_IMAGE_NOT_UPLOADED);
    }

}
