package com.runiverse.running_service.domain.common.exception;

public class InvalidVersionNameFormatException extends BusinessException {

    public InvalidVersionNameFormatException() {
        super(VersionNameErrorCode.INVALID_VERSION_NAME_FORMAT);
    }
}
