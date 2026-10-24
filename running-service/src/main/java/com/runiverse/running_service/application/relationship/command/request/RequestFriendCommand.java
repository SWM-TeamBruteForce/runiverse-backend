package com.runiverse.running_service.application.relationship.command.request;

import java.util.UUID;

public record RequestFriendCommand(
        UUID userId,      // 요청하는 사람 — 토큰 주체
        UUID targetId     // 요청을 받을 사람 — 경로의 {userId}
) {

}
