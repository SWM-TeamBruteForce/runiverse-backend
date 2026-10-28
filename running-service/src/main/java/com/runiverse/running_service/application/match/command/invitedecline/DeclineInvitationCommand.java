package com.runiverse.running_service.application.match.command.invitedecline;

import java.util.UUID;

// 받는 사람은 토큰 주체다 — 이 방에서 나에게 온 초대를 보낸 사람과 상관없이 모두 거절한다
public record DeclineInvitationCommand(UUID userId, Long runningRoomId) {

}
