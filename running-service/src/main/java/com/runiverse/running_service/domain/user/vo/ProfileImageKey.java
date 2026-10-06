package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.ProfileImageKeyRequiredException;
import com.runiverse.running_service.domain.user.exception.ProfileImageKeyTooLongException;

public record ProfileImageKey(String value) {

    private static final int MAX_LENGTH = 255;

    public ProfileImageKey {
        if (value == null) {
            throw new ProfileImageKeyRequiredException();
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new ProfileImageKeyRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new ProfileImageKeyTooLongException();
        }
    }
}
