package com.runiverse.running_service.application.running.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

public class RunningResultNotFoundException extends BusinessException {

    public RunningResultNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
