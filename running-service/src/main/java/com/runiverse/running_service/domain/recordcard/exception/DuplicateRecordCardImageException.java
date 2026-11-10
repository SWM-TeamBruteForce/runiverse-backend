package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class DuplicateRecordCardImageException extends BusinessException {

    public DuplicateRecordCardImageException() {
        super(RecordCardErrorCode.DUPLICATE_RECORD_CARD_IMAGE);
    }
}
