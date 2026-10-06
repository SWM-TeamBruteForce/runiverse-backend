package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.IntroductionRequiredException;
import com.runiverse.running_service.domain.user.exception.IntroductionTooLongException;

public record Introduction(String value) {

    private static final int MAX_LENGTH = 100;

    public Introduction {
        if (value == null) {
            throw new IntroductionRequiredException();
        }

        if (value.length() > MAX_LENGTH) {
            throw new IntroductionTooLongException();
        }
    }
}
