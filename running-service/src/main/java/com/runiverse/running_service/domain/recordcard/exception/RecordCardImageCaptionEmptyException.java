package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardImageCaptionEmptyException extends BusinessException {

    public RecordCardImageCaptionEmptyException() {
        super(RecordCardErrorCode.RECORD_CARD_IMAGE_CAPTION_EMPTY);
    }
}
