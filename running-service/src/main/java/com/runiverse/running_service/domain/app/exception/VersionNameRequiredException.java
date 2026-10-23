package com.runiverse.running_service.domain.app.exception;

import com.runiverse.running_service.domain.common.exception.AppVersionErrorCode;
import com.runiverse.running_service.domain.common.exception.BusinessException;

public class VersionNameRequiredException extends BusinessException {

    public VersionNameRequiredException() {
        super(AppVersionErrorCode.VERSION_NAME_REQUIRED);
    }
}
