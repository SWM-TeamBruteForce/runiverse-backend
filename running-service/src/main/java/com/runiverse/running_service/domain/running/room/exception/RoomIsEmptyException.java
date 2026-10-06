package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class RoomIsEmptyException extends BusinessException {

    public RoomIsEmptyException() {
        super(RunningRoomErrorCode.ROOM_IS_EMPTY);
    }
}
