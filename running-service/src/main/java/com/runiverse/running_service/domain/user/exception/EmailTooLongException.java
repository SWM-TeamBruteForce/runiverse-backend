package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.UserErrorCode;

public class EmailTooLongException extends BusinessException {

    public EmailTooLongException() {
        super(UserErrorCode.EMAIL_TOO_LONG);
    }
}
