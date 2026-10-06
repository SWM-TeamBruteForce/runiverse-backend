package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.command.accountdeletion.DeleteAccountCommand;

public interface DeleteAccountUsecase {

    void handle(DeleteAccountCommand command);
}
