package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class GpsTrackKeyTooLongException extends BusinessException {

    public GpsTrackKeyTooLongException() {
        super(RunningRecordErrorCode.GPS_TRACK_KEY_TOO_LONG);
    }
}
