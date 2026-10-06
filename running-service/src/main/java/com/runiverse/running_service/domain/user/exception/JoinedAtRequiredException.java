package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.DeletedUserErrorCode;

public class JoinedAtRequiredException extends BusinessException {

    public JoinedAtRequiredException() {
        super(DeletedUserErrorCode.JOINED_AT_REQUIRED);
    }
}
