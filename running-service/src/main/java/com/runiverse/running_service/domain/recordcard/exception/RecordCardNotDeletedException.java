package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardNotDeletedException extends BusinessException {

    public RecordCardNotDeletedException() {
        super(RecordCardErrorCode.RECORD_CARD_NOT_DELETED);
    }
}
