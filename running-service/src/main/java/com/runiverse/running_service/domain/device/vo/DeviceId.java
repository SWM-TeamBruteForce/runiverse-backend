package com.runiverse.running_service.domain.device.vo;

import com.runiverse.running_service.domain.device.exception.DeviceIdRequiredException;
import com.runiverse.running_service.domain.device.exception.InvalidDeviceIdLengthException;

// 앱이 설치 때 만들어 보관하는 기기 식별자 — 앱이 정하는 값이라 형식은 보지 않는다
public record DeviceId(String value) {

    private static final int MAX_LENGTH = 100;

    public DeviceId {
        if (value == null || value.isBlank()) {
            throw new DeviceIdRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidDeviceIdLengthException();
        }
    }
}
