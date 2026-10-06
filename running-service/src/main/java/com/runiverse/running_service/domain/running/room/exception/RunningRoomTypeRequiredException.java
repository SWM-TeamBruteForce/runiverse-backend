package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class RunningRoomTypeRequiredException extends BusinessException {

    public RunningRoomTypeRequiredException() {
        super(RunningRoomErrorCode.ROOM_TYPE_REQUIRED);
    }
}
