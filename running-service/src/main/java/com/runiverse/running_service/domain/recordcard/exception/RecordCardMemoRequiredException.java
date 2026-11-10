package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardMemoRequiredException extends BusinessException {

    public RecordCardMemoRequiredException() {
        super(RecordCardErrorCode.RECORD_CARD_MEMO_REQUIRED);
    }
}
