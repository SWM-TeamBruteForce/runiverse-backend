package com.runiverse.running_service.domain.running.room.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRoomErrorCode;

public class InvalidInvitationStatusTransitionException extends BusinessException {

    public InvalidInvitationStatusTransitionException() {
        super(RunningRoomErrorCode.INVALID_INVITATION_STATUS_TRANSITION);
    }
}
