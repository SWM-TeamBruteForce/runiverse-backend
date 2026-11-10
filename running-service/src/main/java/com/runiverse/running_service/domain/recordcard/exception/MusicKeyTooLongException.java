package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class MusicKeyTooLongException extends BusinessException {

    public MusicKeyTooLongException() {
        super(RecordCardErrorCode.MUSIC_KEY_TOO_LONG);
    }
}
