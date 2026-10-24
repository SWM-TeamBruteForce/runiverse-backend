package com.runiverse.running_service.application.relationship.port.in;

import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestCommand;
import com.runiverse.running_service.application.relationship.command.accept.AcceptFriendRequestResult;

public interface AcceptFriendRequestUsecase {

    AcceptFriendRequestResult handle(AcceptFriendRequestCommand command);
}
