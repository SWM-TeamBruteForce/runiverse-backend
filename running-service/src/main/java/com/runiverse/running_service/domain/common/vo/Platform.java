package com.runiverse.running_service.domain.common.vo;

import com.runiverse.running_service.domain.common.exception.PlatformRequiredException;
import com.runiverse.running_service.domain.common.exception.UnsupportedPlatformException;

import java.util.Locale;

public enum Platform {
    IOS,
    ANDROID;

    public static Platform from(String value) {
        if (value == null) {
            throw new PlatformRequiredException();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new PlatformRequiredException();
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedPlatformException();
        }
    }
}
