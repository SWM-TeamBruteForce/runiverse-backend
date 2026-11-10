package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardImageKeyRequiredException extends BusinessException {

    public RecordCardImageKeyRequiredException() {
        super(RecordCardErrorCode.RECORD_CARD_IMAGE_KEY_REQUIRED);
    }
}
