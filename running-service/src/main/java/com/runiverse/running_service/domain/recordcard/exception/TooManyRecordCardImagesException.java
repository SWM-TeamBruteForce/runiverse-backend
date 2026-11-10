package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class TooManyRecordCardImagesException extends BusinessException {

    public TooManyRecordCardImagesException() {
        super(RecordCardErrorCode.TOO_MANY_RECORD_CARD_IMAGES);
    }
}
