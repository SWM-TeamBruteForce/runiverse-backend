package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class ProfileImageNotUploadedException extends BusinessException {

    public ProfileImageNotUploadedException() {
        super(UserErrorCode.PROFILE_IMAGE_NOT_UPLOADED);
    }
}
