package com.runiverse.running_service.application.auth.exception;

import com.runiverse.running_service.application.common.exception.AuthErrorCode;
import com.runiverse.running_service.application.common.exception.BusinessException;

public class EmailSendFailedException extends BusinessException {

    public EmailSendFailedException() {
        super(AuthErrorCode.EMAIL_SEND_FAILED);
    }
}
