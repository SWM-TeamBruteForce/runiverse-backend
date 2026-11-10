package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardImageNotInCardException extends BusinessException {

    public RecordCardImageNotInCardException() {
        super(RecordCardErrorCode.RECORD_CARD_IMAGE_NOT_IN_CARD);
    }
}
