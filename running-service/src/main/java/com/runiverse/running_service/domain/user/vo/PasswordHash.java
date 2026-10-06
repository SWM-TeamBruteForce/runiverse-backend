package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.InvalidPasswordHashFormatException;
import com.runiverse.running_service.domain.user.exception.PasswordHashRequiredException;

import java.util.regex.Pattern;

public record PasswordHash(String value) {

    private static final Pattern ARGON2_PATTERN = Pattern.compile(
            "^\\$argon2id\\$v=\\d+\\$m=\\d+,t=\\d+,p=\\d+\\$[A-Za-z0-9+/]+={0,2}\\$[A-Za-z0-9+/]+={0,2}$"
    );

    public PasswordHash {
        if (value == null) {
            throw new PasswordHashRequiredException();
        }

        if (!value.isEmpty() && !ARGON2_PATTERN.matcher(value).matches()) {
            throw new InvalidPasswordHashFormatException();
        }
    }
}
