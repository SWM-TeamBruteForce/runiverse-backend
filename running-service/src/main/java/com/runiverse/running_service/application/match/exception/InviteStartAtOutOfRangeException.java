package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.InviteErrorCode;

public class InviteStartAtOutOfRangeException extends BusinessException {

    public InviteStartAtOutOfRangeException() {
        super(InviteErrorCode.INVITE_START_AT_OUT_OF_RANGE);
    }
}
