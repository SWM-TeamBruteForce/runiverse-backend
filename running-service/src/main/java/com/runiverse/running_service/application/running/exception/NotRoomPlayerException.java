package com.runiverse.running_service.application.running.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.RunningErrorCode;

public class NotRoomPlayerException extends BusinessException {

    public NotRoomPlayerException() {
        super(RunningErrorCode.NOT_ROOM_PLAYER);
    }
}
