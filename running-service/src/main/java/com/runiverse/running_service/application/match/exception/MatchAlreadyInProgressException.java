package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.MatchErrorCode;

public class MatchAlreadyInProgressException extends BusinessException {

    public MatchAlreadyInProgressException() {
        super(MatchErrorCode.MATCH_ALREADY_IN_PROGRESS);
    }
}
