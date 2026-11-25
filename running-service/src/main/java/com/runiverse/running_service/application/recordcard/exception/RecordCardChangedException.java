package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RecordCardErrorCode;

// 수정 초안을 만든 뒤 카드가 다른 수정으로 바뀌었다 — 옛 모습을 기준으로 만든 초안이라 덮어쓰지 않는다(409)
public class RecordCardChangedException extends BusinessException {

    public RecordCardChangedException() {
        super(RecordCardErrorCode.RECORD_CARD_CHANGED);
    }
}
