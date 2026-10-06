package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class NotRoomPlayerException extends BusinessException {

    public NotRoomPlayerException() {
        super(RunningRoomErrorCode.NOT_ROOM_PLAYER);
    }
}
