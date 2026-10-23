package com.runiverse.running_service.domain.common.exception;

public class UnsupportedPlatformException extends BusinessException {

    public UnsupportedPlatformException() {
        super(PlatformErrorCode.UNSUPPORTED_PLATFORM);
    }
}
