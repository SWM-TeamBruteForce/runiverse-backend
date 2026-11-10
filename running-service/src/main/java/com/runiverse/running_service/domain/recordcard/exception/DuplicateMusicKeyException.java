package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class DuplicateMusicKeyException extends BusinessException {

    public DuplicateMusicKeyException() {
        super(RecordCardErrorCode.DUPLICATE_MUSIC_KEY);
    }
}
