package com.runiverse.running_service.domain.user.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.OauthUserErrorCode;

public class OauthNotLinkedException extends BusinessException {

    public OauthNotLinkedException() {
        super(OauthUserErrorCode.OAUTH_NOT_LINKED);
    }
}
