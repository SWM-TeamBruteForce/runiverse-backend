package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class ProfileImageKeyTooLongException extends BusinessException {

    public ProfileImageKeyTooLongException() {
        super(UserErrorCode.PROFILE_IMAGE_KEY_TOO_LONG);
    }

}
