package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class InvalidRecordCardIdException extends BusinessException {

    public InvalidRecordCardIdException() {
        super(RecordCardErrorCode.INVALID_RECORD_CARD_ID);
    }
}
