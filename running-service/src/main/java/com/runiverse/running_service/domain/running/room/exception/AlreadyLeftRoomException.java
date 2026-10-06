package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class AlreadyLeftRoomException extends BusinessException {

    public AlreadyLeftRoomException() {
        super(RunningRoomErrorCode.ALREADY_LEFT_ROOM);
    }
}
