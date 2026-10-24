package com.runiverse.running_service.application.relationship.command.accept;

import java.util.UUID;

public record AcceptFriendRequestCommand(
        UUID userId,    // 수락하는 사람 — 토큰 주체
        UUID requesterId    // 요청을 보낸 사람 — 경로의 {userId}
) {

}
