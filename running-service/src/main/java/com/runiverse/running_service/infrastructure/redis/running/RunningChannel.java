package com.runiverse.running_service.infrastructure.redis.running;

public final class RunningChannel {

    // 모든 인스턴스가 상시 구독 하는 고정 채널
    private static final String PROGRESS_PREFIX = "running:room:";

    // 방마다 채널이 갈린다 - 참가자를 든 인스턴스만 구독
    public static String room(Long runningRoomId) {
        return PROGRESS_PREFIX + runningRoomId;
    }

    private RunningChannel() {
    }
}
