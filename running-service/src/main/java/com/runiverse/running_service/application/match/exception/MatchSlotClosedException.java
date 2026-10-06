package com.runiverse.running_service.application.match.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.MatchErrorCode;

public class MatchSlotClosedException extends BusinessException {

    public MatchSlotClosedException() {
        super(MatchErrorCode.MATCH_SLOT_CLOSED);
    }
}
