package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.DeletedUserErrorCode;

public class BmiOutOfRangeException extends BusinessException {

    public BmiOutOfRangeException() {
        super(DeletedUserErrorCode.BMI_OUT_OF_RANGE);
    }
}
