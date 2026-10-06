package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class ProfileImageKeyRequiredException extends BusinessException {

    public ProfileImageKeyRequiredException() {
        super(UserErrorCode.PROFILE_IMAGE_KEY_REQUIRED);
    }

}
