package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class AccountDeletionUnavailableException extends BusinessException {

    public AccountDeletionUnavailableException() {
        super(UserErrorCode.ACCOUNT_DELETION_UNAVAILABLE);
    }
}
