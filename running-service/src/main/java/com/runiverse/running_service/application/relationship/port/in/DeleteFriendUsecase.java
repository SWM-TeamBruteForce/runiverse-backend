package com.runiverse.running_service.application.relationship.port.in;

import com.runiverse.running_service.application.relationship.command.delete.DeleteFriendCommand;

public interface DeleteFriendUsecase {

    void handle(DeleteFriendCommand command);
}
