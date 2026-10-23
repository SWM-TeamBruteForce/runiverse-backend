package com.runiverse.running_service.domain.running.combo.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningComboErrorCode;

public class ComboUsersNotOrderedException extends BusinessException {

    public ComboUsersNotOrderedException() {
        super(RunningComboErrorCode.COMBO_USERS_NOT_ORDERED);
    }
}
