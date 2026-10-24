package com.runiverse.running_service.application.relationship.command.delete;

import java.util.UUID;

public record DeleteFriendCommand(
        UUID userId,    // 삭제하는 사람 — 토큰 주체
        UUID friendId   // 삭제할 친구 — 경로의 {userId}
) {

}
