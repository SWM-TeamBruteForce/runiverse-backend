package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationResult;

public interface AcceptInvitationUsecase {

    AcceptInvitationResult handle(AcceptInvitationCommand command);
}
