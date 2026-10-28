package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.invitedecline.DeclineInvitationCommand;

public interface DeclineInvitationUsecase {

    // 204라 돌려줄 것이 없다
    void handle(DeclineInvitationCommand command);
}
