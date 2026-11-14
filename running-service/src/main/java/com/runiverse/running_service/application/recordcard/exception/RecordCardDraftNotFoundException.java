package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 확정할 초안이 없다 — 없거나, 유효 시간이 지났거나, 다른 사람의 초안이다. 셋을 가르지 않는다
public class RecordCardDraftNotFoundException extends BusinessException {

    public RecordCardDraftNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
