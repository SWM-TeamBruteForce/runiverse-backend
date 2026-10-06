package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class UnsupportedProfileVisibilityException extends BusinessException {

    public UnsupportedProfileVisibilityException() {
        super(UserErrorCode.UNSUPPORTED_PROFILE_VISIBILITY);
    }
}
