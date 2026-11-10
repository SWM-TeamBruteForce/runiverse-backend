package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class InvalidDisplayOrderException extends BusinessException {

    public InvalidDisplayOrderException() {
        super(RecordCardErrorCode.INVALID_DISPLAY_ORDER);
    }
}
