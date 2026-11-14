package com.runiverse.running_service.application.recordcard.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.ResourceErrorCode;

// 카드를 붙일 기록이 없다 — 없거나, 남의 기록이거나, 숨긴 방의 기록이다. 셋을 가르지 않는다
public class RunningRecordNotFoundException extends BusinessException {

    public RunningRecordNotFoundException() {
        super(ResourceErrorCode.NOT_FOUND);
    }
}
