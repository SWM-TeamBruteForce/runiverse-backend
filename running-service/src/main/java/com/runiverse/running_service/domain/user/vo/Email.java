package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.EmailRequiredException;
import com.runiverse.running_service.domain.user.exception.EmailTooLongException;
import com.runiverse.running_service.domain.user.exception.InvalidEmailFormatException;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final int MAX_LENGTH = 254;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public Email {
        if (value == null) {
            throw new EmailRequiredException();
        }

        value = value.trim().toLowerCase(Locale.ROOT);

        if (value.isEmpty()) {
            throw new EmailRequiredException();
        }

        if (value.length() > MAX_LENGTH) {
            throw new EmailTooLongException();
        }

        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new InvalidEmailFormatException();
        }
    }

}
