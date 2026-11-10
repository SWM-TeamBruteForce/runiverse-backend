package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class InvalidRecordCardImageIdException extends BusinessException {

    public InvalidRecordCardImageIdException() {
        super(RecordCardErrorCode.INVALID_RECORD_CARD_IMAGE_ID);
    }
}
