package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class InvalidRunningRoomIdException extends BusinessException {

    public InvalidRunningRoomIdException() {
        super(RunningRoomErrorCode.INVALID_RUNNING_ROOM_ID);
    }
}
