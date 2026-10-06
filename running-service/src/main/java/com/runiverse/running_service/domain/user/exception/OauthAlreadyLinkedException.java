package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.OauthUserErrorCode;

public class OauthAlreadyLinkedException extends BusinessException {

    public OauthAlreadyLinkedException() {
        super(OauthUserErrorCode.OAUTH_ALREADY_LINKED);
    }
}
