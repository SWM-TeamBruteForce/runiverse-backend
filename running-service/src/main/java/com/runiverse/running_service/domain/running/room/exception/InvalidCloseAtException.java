package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class InvalidCloseAtException extends BusinessException {

    public InvalidCloseAtException() {
        super(RunningRoomErrorCode.INVALID_CLOSE_AT);
    }
}
