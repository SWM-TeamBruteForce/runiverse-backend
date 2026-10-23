package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.BackgroundImageKeyRequiredException;
import com.runiverse.running_service.domain.user.exception.BackgroundImageKeyTooLongException;

public record BackgroundImageKey(String value) {

    private static final int MAX_LENGTH = 255;

    public BackgroundImageKey {
        if (value == null) {
            throw new BackgroundImageKeyRequiredException();
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new BackgroundImageKeyRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new BackgroundImageKeyTooLongException();
        }
    }
}
