package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RecordCardErrorCode;

// 같은 초안의 확정이 이미 처리 중(잠금을 못 잡음)이거나, 같은 사람의 다른 카드 확정과 순위가 겹쳤다 — 기다리지 않는다
public class RecordCardDraftInProgressException extends BusinessException {

    public RecordCardDraftInProgressException() {
        super(RecordCardErrorCode.RECORD_CARD_DRAFT_IN_PROGRESS);
    }
}
