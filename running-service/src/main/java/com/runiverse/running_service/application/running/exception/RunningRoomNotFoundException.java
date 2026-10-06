package com.runiverse.running_service.application.running.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;

public class RunningRoomNotFoundException extends BusinessException {

    public RunningRoomNotFoundException() {
        super(RunningErrorCode.ROOM_NOT_FOUND);
    }
}
