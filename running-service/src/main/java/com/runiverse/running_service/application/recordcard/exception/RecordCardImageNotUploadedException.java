package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RecordCardErrorCode;

// 초안에 적힌 사진이 S3에 없다 — 안 올렸거나 나눠 올린 조각이 빠졌다. 초안은 그대로라 빠진 것만 다시 올리면 된다
public class RecordCardImageNotUploadedException extends BusinessException {

    public RecordCardImageNotUploadedException() {
        super(RecordCardErrorCode.RECORD_CARD_IMAGE_NOT_UPLOADED);
    }
}
