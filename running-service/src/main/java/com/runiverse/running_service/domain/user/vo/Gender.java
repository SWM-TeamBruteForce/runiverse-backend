package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.GenderRequiredException;
import com.runiverse.running_service.domain.user.exception.UnsupportedGenderException;

import java.util.Locale;

public enum Gender {
    MALE,
    FEMALE;

    public static Gender from(String value) {
        if (value == null) {
            throw new GenderRequiredException();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new GenderRequiredException();
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedGenderException();
        }
    }
}
