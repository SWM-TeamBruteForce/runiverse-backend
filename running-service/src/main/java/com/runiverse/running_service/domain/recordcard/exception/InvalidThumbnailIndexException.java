package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class InvalidThumbnailIndexException extends BusinessException {

    public InvalidThumbnailIndexException() {
        super(RecordCardErrorCode.INVALID_THUMBNAIL_INDEX);
    }
}
