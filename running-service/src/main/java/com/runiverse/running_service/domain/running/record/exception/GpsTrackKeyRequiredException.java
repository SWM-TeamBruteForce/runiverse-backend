package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class GpsTrackKeyRequiredException extends BusinessException {

    public GpsTrackKeyRequiredException() {
        super(RunningRecordErrorCode.GPS_TRACK_KEY_REQUIRED);
    }
}
