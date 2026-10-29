package com.runiverse.running_service.application.notification.command.broadcast;

import java.util.UUID;

// 다른 인스턴스가 커밋 뒤 Redis로 알린 "이 사람에게 이 알림이 생겼다"
public record BroadcastUserNotificationCommand(UUID userId, Long notificationId) {

}
