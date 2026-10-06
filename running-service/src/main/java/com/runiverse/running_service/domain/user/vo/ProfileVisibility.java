package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.ProfileVisibilityRequiredException;
import com.runiverse.running_service.domain.user.exception.UnsupportedProfileVisibilityException;

import java.util.Locale;

public enum ProfileVisibility {
    FRIENDS,
    PUBLIC;

    public static ProfileVisibility from(String value) {
        if (value == null) {
            throw new ProfileVisibilityRequiredException();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ProfileVisibilityRequiredException();
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedProfileVisibilityException();
        }
    }
}
