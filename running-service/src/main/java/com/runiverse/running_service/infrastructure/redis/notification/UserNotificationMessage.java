package com.runiverse.running_service.infrastructure.redis.notification;

import java.util.UUID;

// 알림 내용은 싣지 않는다 — 받는 사람이 붙은 인스턴스만 DB에서 읽어 조립한다
public record UserNotificationMessage(UUID userId, Long notificationId) {

}
