package com.runiverse.running_service.domain.running.player.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningPlayerErrorCode;

public class PlayerStartAtRequiredException extends BusinessException {

    public PlayerStartAtRequiredException() {
        super(RunningPlayerErrorCode.START_AT_REQUIRED);
    }
}
