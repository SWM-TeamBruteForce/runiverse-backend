package com.runiverse.running_service.domain.running.player.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningPlayerErrorCode;

public class InvalidPlayerStatusTransitionException extends BusinessException {

    public InvalidPlayerStatusTransitionException() {
        super(RunningPlayerErrorCode.INVALID_PLAYER_STATUS_TRANSITION);
    }
}
