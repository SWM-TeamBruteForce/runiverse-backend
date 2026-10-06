package com.runiverse.running_service.infrastructure.redis;

public enum RedisKey {
    USER("user"),
    MATCH("match"),
    RUNNING_TRACK("running:track"),
    RUNNING_COMBO("running:combo");

    private static final String DELIMITER = ":";
    private final String prefix;

    RedisKey(String prefix) {
        this.prefix = prefix;
    }

    public String of(String... segments) {
        return prefix + DELIMITER + String.join(DELIMITER, segments);
    }
}
