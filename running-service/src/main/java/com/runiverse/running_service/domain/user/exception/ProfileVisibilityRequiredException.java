package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class ProfileVisibilityRequiredException extends BusinessException {

    public ProfileVisibilityRequiredException() {
        super(UserErrorCode.PROFILE_VISIBILITY_REQUIRED);
    }
}
