package com.runiverse.running_service.application.match.port.in;

import com.runiverse.running_service.application.match.command.invitesend.SendInvitationCommand;

public interface SendInvitationUsecase {

    // 204라 돌려줄 것이 없다 — 못 보내면 예외로 이유를 알린다
    void handle(SendInvitationCommand command);
}
