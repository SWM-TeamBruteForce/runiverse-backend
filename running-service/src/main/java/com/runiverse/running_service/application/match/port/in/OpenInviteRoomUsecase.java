package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomResult;

public interface OpenInviteRoomUsecase {

    OpenInviteRoomResult handle(OpenInviteRoomCommand command);
}
