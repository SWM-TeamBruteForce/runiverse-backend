package com.runiverse.running_service.application.relationship.port.in;

import com.runiverse.running_service.application.relationship.command.cancel.CancelFriendRequestCommand;

public interface CancelFriendRequestUsecase {

    void handle(CancelFriendRequestCommand command);
}
