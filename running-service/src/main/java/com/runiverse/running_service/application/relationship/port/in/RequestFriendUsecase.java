package com.runiverse.running_service.application.relationship.port.in;

import com.runiverse.running_service.application.relationship.command.request.RequestFriendCommand;
import com.runiverse.running_service.application.relationship.command.request.RequestFriendResult;

public interface RequestFriendUsecase {

    RequestFriendResult handle(RequestFriendCommand command);
}
