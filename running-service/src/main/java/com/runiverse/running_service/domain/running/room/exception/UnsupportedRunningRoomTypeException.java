package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class UnsupportedRunningRoomTypeException extends BusinessException {

    public UnsupportedRunningRoomTypeException() {
        super(RunningRoomErrorCode.UNSUPPORTED_ROOM_TYPE);
    }
}
