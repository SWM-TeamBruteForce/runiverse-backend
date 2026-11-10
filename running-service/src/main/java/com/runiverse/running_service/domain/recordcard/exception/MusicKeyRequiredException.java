package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class MusicKeyRequiredException extends BusinessException {

    public MusicKeyRequiredException() {
        super(RecordCardErrorCode.MUSIC_KEY_REQUIRED);
    }
}
