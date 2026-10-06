package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.MatchErrorCode;

public class MatchAlreadyStartedException extends BusinessException {

    public MatchAlreadyStartedException() {
        super(MatchErrorCode.MATCH_ALREADY_STARTED);
    }
}
