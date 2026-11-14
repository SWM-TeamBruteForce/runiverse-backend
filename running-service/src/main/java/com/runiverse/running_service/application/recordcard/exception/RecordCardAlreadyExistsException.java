package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RecordCardErrorCode;

// 이 기록에 카드가 이미 있다 — 작성 요청(9-1)은 빠른 거절, 확정(9-2)은 그사이 다른 초안이 먼저 확정한 경우다
public class RecordCardAlreadyExistsException extends BusinessException {

    public RecordCardAlreadyExistsException() {
        super(RecordCardErrorCode.RECORD_CARD_ALREADY_EXISTS);
    }
}
