package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class RecordCardMemoTooLongException extends BusinessException {

    public RecordCardMemoTooLongException() {
        super(RecordCardErrorCode.RECORD_CARD_MEMO_TOO_LONG);
    }
}
