package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class RoomNotJoinableException extends BusinessException {

    public RoomNotJoinableException() {
        super(RunningRoomErrorCode.ROOM_NOT_JOINABLE);
    }
}
