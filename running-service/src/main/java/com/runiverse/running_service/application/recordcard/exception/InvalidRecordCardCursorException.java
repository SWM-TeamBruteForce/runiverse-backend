package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RecordCardErrorCode;

// 목록(10-3) 커서가 깨졌다 — 서버가 준 값이 아니다
public class InvalidRecordCardCursorException extends BusinessException {

    public InvalidRecordCardCursorException() {
        super(RecordCardErrorCode.INVALID_RECORD_CARD_CURSOR);
    }
}
