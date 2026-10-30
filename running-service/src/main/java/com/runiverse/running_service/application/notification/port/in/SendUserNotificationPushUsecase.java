package com.runiverse.running_service.application.notification.port.in;

import com.runiverse.running_service.application.notification.command.push.SendUserNotificationPushCommand;

public interface SendUserNotificationPushUsecase {

    void handle(SendUserNotificationPushCommand command);
}
