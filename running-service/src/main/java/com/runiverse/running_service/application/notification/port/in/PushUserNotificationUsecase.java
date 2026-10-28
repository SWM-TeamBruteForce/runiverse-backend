package com.runiverse.running_service.application.notification.port.in;

import com.runiverse.running_service.application.notification.command.push.PushUserNotificationCommand;

public interface PushUserNotificationUsecase {

    void handle(PushUserNotificationCommand command);
}
