package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class DeletedAtRequiredException extends BusinessException {

    public DeletedAtRequiredException() {
        super(RecordCardErrorCode.DELETED_AT_REQUIRED);
    }
}
