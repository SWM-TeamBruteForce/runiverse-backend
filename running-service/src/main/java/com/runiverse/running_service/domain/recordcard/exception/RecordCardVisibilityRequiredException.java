package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardVisibilityRequiredException extends BusinessException {

    public RecordCardVisibilityRequiredException() {
        super(RecordCardErrorCode.RECORD_CARD_VISIBILITY_REQUIRED);
    }
}
