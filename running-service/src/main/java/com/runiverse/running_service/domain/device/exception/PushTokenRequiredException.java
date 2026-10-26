package com.runiverse.running_service.domain.device.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserDeviceErrorCode;

public class PushTokenRequiredException extends BusinessException {

    public PushTokenRequiredException() {
        super(UserDeviceErrorCode.PUSH_TOKEN_REQUIRED);
    }
}
