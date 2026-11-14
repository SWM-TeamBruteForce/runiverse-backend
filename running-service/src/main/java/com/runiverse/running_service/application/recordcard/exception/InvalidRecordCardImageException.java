package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RecordCardErrorCode;

// 올라간 파일의 크기·형식이 작성 요청에서 받은 값과 다르다 — 서명이 막아 정상 경로에서는 오지 않는다
public class InvalidRecordCardImageException extends BusinessException {

    public InvalidRecordCardImageException() {
        super(RecordCardErrorCode.INVALID_RECORD_CARD_IMAGE);
    }
}
