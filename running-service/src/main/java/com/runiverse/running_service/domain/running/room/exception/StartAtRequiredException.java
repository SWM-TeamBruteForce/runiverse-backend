package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class StartAtRequiredException extends BusinessException {

    public StartAtRequiredException() {
        super(RunningRoomErrorCode.START_AT_REQUIRED);
    }
}
