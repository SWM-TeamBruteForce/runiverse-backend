package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class InvalidLeaveCountException extends BusinessException {

    public InvalidLeaveCountException() {
        super(RunningRoomErrorCode.INVALID_LEAVE_COUNT);
    }
}
