package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class AlreadyRoomPlayerException extends BusinessException {

    public AlreadyRoomPlayerException() {
        super(RunningRoomErrorCode.ALREADY_ROOM_PLAYER);
    }
}
