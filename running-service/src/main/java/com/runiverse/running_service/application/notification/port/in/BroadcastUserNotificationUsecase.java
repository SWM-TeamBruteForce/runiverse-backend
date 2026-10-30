package com.runiverse.running_service.application.notification.port.in;

import com.runiverse.running_service.application.notification.command.broadcast.BroadcastUserNotificationCommand;

public interface BroadcastUserNotificationUsecase {

    void handle(BroadcastUserNotificationCommand command);
}
