package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class InvalidInviteCountException extends BusinessException {

    public InvalidInviteCountException() {
        super(RunningRoomErrorCode.INVALID_INVITE_COUNT);
    }
}
