package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class InvalidRoomStatusTransitionException extends BusinessException {

    public InvalidRoomStatusTransitionException() {
        super(RunningRoomErrorCode.INVALID_ROOM_STATUS_TRANSITION);
    }
}
