package com.runiverse.running_service.infrastructure.redis;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RedisKey {
    USER("user"),
    MATCH("match"),
    RUNNING_TRACK("running:track"),
    RUNNING_COMBO("running:combo");

    private static final String DELIMITER = ":";
    private final String prefix;

    public String of(String... segments) {
        return prefix + DELIMITER + String.join(DELIMITER, segments);
    }
}
