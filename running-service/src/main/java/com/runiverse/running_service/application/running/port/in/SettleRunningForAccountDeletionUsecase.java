package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.command.accountdeletion.SettleRunningForAccountDeletionCommand;

public interface SettleRunningForAccountDeletionUsecase {

    void handle(SettleRunningForAccountDeletionCommand command);
}
