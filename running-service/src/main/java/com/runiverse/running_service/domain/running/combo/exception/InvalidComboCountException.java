package com.runiverse.running_service.domain.running.combo.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningComboErrorCode;

public class InvalidComboCountException extends BusinessException {

    public InvalidComboCountException() {
        super(RunningComboErrorCode.INVALID_COMBO_COUNT);
    }
}
