package com.runiverse.running_service.domain.common.exception;

public class PlatformRequiredException extends BusinessException {

    public PlatformRequiredException() {
        super(PlatformErrorCode.PLATFORM_REQUIRED);
    }
}
