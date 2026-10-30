package com.runiverse.running_service.application.notification.command.push;

import java.util.UUID;

public record SendUserNotificationPushCommand(UUID userId, Long notificationId) {

}
