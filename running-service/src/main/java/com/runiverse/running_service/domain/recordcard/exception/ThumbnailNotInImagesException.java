package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class ThumbnailNotInImagesException extends BusinessException {

    public ThumbnailNotInImagesException() {
        super(RecordCardErrorCode.THUMBNAIL_NOT_IN_IMAGES);
    }
}
