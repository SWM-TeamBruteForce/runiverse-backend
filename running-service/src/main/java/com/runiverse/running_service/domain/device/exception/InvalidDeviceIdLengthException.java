package com.runiverse.running_service.domain.device.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserDeviceErrorCode;

public class InvalidDeviceIdLengthException extends BusinessException {

    public InvalidDeviceIdLengthException() {
        super(UserDeviceErrorCode.INVALID_DEVICE_ID_LENGTH);
    }
}
