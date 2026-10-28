package com.runiverse.running_service.application.notification.port.in;

import com.runiverse.running_service.application.notification.command.read.ReadAllUserNotificationsCommand;

public interface ReadAllUserNotificationsUsecase {

    void handle(ReadAllUserNotificationsCommand command);
}
