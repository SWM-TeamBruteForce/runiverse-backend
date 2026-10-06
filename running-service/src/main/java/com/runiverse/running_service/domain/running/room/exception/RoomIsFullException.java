package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class RoomIsFullException extends BusinessException {

    public RoomIsFullException() {
        super(RunningRoomErrorCode.ROOM_IS_FULL);
    }
}
