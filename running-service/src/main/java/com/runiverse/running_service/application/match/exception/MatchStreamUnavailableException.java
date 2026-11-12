package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.MatchErrorCode;

public class MatchStreamUnavailableException extends BusinessException {

    public MatchStreamUnavailableException() {
        super(MatchErrorCode.MATCH_STREAM_UNAVAILABLE);
    }
}
