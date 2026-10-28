package com.runiverse.running_service.application.match.command.inviteaccept;

import java.util.UUID;

// 받는 사람은 토큰 주체다 — 누가 보냈든 받는 사람 기준이라 보낸 사람을 고르지 않는다
public record AcceptInvitationCommand(UUID userId, Long runningRoomId) {

}
