package com.runiverse.running_service.domain.running.combo.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningComboErrorCode;

public class ComboRouteSizeMismatchException extends BusinessException {

    public ComboRouteSizeMismatchException() {
        super(RunningComboErrorCode.COMBO_ROUTE_SIZE_MISMATCH);
    }
}
