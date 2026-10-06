package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.OauthUserErrorCode;

public class LastSignInMethodException extends BusinessException {

    public LastSignInMethodException() {
        super(OauthUserErrorCode.LAST_SIGN_IN_METHOD);
    }
}
