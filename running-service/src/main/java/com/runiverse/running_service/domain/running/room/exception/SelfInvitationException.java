package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class SelfInvitationException extends BusinessException {

    public SelfInvitationException() {
        super(RunningRoomErrorCode.SELF_INVITATION);
    }
}
