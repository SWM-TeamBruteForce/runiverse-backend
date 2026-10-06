package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.ProviderIdRequiredException;
import com.runiverse.running_service.domain.user.exception.ProviderIdTooLongException;

public record ProviderId(String value) {

    private static final int MAX_LENGTH = 255;

    public ProviderId {
        if (value == null) {
            throw new ProviderIdRequiredException();
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new ProviderIdRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new ProviderIdTooLongException();
        }
    }
}
