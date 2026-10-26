package com.runiverse.running_service.domain.device.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserDeviceErrorCode;

public class DeviceIdRequiredException extends BusinessException {

    public DeviceIdRequiredException() {
        super(UserDeviceErrorCode.DEVICE_ID_REQUIRED);
    }
}
