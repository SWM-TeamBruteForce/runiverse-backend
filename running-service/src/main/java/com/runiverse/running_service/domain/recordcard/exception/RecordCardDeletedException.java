package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardDeletedException extends BusinessException {

    public RecordCardDeletedException() {
        super(RecordCardErrorCode.RECORD_CARD_DELETED);
    }
}
