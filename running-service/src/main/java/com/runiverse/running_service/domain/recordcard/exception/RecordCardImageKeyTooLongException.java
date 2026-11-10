package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardImageKeyTooLongException extends BusinessException {

    public RecordCardImageKeyTooLongException() {
        super(RecordCardErrorCode.RECORD_CARD_IMAGE_KEY_TOO_LONG);
    }
}
