package com.runiverse.running_service.infrastructure.redis.match;

public final class MatchChannel {

    private static final String ROOM_PREFIX = "match:room:";

    public static String room(Long runningRoomId) {
        return ROOM_PREFIX + runningRoomId;
    }

    private MatchChannel() {
    }
}
