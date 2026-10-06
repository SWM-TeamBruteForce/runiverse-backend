package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class InvalidPlayerCountException extends BusinessException {

    public InvalidPlayerCountException() {
        super(RunningRoomErrorCode.INVALID_PLAYER_COUNT);
    }
}
