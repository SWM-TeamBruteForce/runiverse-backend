package com.runiverse.running_service.application.relationship.command.cancel;

import java.util.UUID;

public record CancelFriendRequestCommand(
        UUID userId,    // 취소·거절하는 사람 — 토큰 주체
        UUID targetId   // 요청을 주고받은 상대 — 경로의 {userId}
) {

}
