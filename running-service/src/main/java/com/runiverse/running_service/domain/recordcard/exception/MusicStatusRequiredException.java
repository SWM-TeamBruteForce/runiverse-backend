package com.runiverse.running_service.domain.recordcard.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RecordCardErrorCode;

public class MusicStatusRequiredException extends BusinessException {

    public MusicStatusRequiredException() {
        super(RecordCardErrorCode.MUSIC_STATUS_REQUIRED);
    }
}
