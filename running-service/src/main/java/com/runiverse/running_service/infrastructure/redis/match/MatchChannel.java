package com.runiverse.running_service.infrastructure.redis.match;

public final class MatchChannel {

    // 방 채널과 달리 하나다 — 옛 연결이 어느 인스턴스에 있는지 모르고, 방을 옮기면 옛 인스턴스는 새 방을 구독하지 않으므로 모든 인스턴스가 듣는다
    public static final String SUPERSEDE = "match:stream:supersede";
    private static final String ROOM_PREFIX = "match:room:";

    public static String room(Long runningRoomId) {
        return ROOM_PREFIX + runningRoomId;
    }

    private MatchChannel() {
    }
}
