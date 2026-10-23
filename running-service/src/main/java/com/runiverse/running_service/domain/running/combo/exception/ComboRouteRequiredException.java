package com.runiverse.running_service.domain.running.combo.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningComboErrorCode;

public class ComboRouteRequiredException extends BusinessException {

    public ComboRouteRequiredException() {
        super(RunningComboErrorCode.COMBO_ROUTE_REQUIRED);
    }
}
