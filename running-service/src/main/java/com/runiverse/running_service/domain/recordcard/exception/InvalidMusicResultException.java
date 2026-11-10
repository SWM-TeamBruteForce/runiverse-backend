package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class InvalidMusicResultException extends BusinessException {

    public InvalidMusicResultException() {
        super(RecordCardErrorCode.INVALID_MUSIC_RESULT);
    }
}
