package com.runiverse.running_service.infrastructure.redis.scheduling;

public final class ScheduleChannel {

    // 방마다 나뉘는 매칭 채널과 달리 하나다 — 모든 인스턴스가 모든 예약을 들어야 한다
    public static final String JOB = "schedule:job";

    private ScheduleChannel() {
    }
}
