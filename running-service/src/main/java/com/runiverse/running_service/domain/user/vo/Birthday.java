package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.BirthdayRequiredException;
import com.runiverse.running_service.domain.user.exception.InvalidBirthdayException;

import java.time.LocalDate;

public record Birthday(LocalDate value) {

    private static final LocalDate MIN = LocalDate.of(1900, 1, 1); // 1900년대 이전 사람은 불가능

    public Birthday {
        if (value == null) {
            throw new BirthdayRequiredException();
        }
        if (value.isAfter(LocalDate.now()) || value.isBefore(MIN)) {
            throw new InvalidBirthdayException();
        }
    }
}
