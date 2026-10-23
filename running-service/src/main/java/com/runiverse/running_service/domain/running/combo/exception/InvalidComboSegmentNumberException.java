package com.runiverse.running_service.domain.running.combo.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningComboErrorCode;

public class InvalidComboSegmentNumberException extends BusinessException {

    public InvalidComboSegmentNumberException() {
        super(RunningComboErrorCode.INVALID_COMBO_SEGMENT_NUMBER);
    }
}
