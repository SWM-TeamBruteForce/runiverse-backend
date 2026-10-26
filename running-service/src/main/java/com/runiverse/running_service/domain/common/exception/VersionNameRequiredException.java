package com.runiverse.running_service.domain.common.exception;

public class VersionNameRequiredException extends BusinessException {

    public VersionNameRequiredException() {
        super(VersionNameErrorCode.VERSION_NAME_REQUIRED);
    }
}
