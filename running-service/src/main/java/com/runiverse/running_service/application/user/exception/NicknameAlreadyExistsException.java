package com.runiverse.running_service.application.user.exception;

import com.runiverse.running_service.application.common.exception.BusinessException;
import com.runiverse.running_service.application.common.exception.UserErrorCode;

public class NicknameAlreadyExistsException extends BusinessException {

    public NicknameAlreadyExistsException() {
        super(UserErrorCode.NICKNAME_ALREADY_EXISTS);
    }
}
