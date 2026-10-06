package com.runiverse.running_service.domain.common.vo;

import com.runiverse.running_service.domain.common.exception.InvalidUserIdFormatException;
import com.runiverse.running_service.domain.common.exception.UserIdRequiredException;

import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new UserIdRequiredException();
        }

        if (value.version() != 7) {
            throw new InvalidUserIdFormatException();
        }
    }
}
