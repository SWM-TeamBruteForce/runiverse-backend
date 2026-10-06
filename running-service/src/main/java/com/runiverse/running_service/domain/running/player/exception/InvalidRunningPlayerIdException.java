package com.runiverse.running_service.domain.running.player.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningPlayerErrorCode;

public class InvalidRunningPlayerIdException extends BusinessException {

    public InvalidRunningPlayerIdException() {
        super(RunningPlayerErrorCode.INVALID_RUNNING_PLAYER_ID);
    }
}
