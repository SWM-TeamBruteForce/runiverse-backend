package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.InvalidNicknameFormatException;
import com.runiverse.running_service.domain.user.exception.InvalidNicknameLengthException;
import com.runiverse.running_service.domain.user.exception.NicknameRequiredException;

import java.util.regex.Pattern;

public record Nickname(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 16;
    private static final Pattern PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_]+$");

    public Nickname {
        if (value == null) {
            throw new NicknameRequiredException();
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new NicknameRequiredException();
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidNicknameLengthException();
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new InvalidNicknameFormatException();
        }
    }
}
