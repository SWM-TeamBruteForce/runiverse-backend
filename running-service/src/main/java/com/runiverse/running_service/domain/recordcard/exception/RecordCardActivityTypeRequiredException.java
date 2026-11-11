package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardActivityTypeRequiredException extends BusinessException {

    public RecordCardActivityTypeRequiredException() {
        super(RecordCardErrorCode.RECORD_CARD_ACTIVITY_TYPE_REQUIRED);
    }
}
