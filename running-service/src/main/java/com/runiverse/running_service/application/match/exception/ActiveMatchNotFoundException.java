package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

public class ActiveMatchNotFoundException extends BusinessException {

    public ActiveMatchNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
