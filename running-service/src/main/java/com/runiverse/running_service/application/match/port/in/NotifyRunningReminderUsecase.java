package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.reminder.NotifyRunningReminderCommand;

public interface NotifyRunningReminderUsecase {

    void handle(NotifyRunningReminderCommand command);
}
