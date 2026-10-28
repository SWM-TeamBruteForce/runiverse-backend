package com.runiverse.running_service.infrastructure.redis.notification;

public final class NotificationChannel {

    // 예약 채널처럼 하나다 — 받는 사람이 어느 인스턴스에 붙었는지 모르므로 모든 인스턴스가 듣는다
    public static final String USER = "notification:user";

    private NotificationChannel() {
    }
}
