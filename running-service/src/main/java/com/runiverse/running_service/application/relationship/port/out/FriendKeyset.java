package com.runiverse.running_service.application.relationship.port.out;

import java.time.LocalDateTime;
import java.util.UUID;

// 묶음 안에서 이 항목 다음부터 읽는다 — updated_at·userId 역순이라 둘 다 있어야 위치가 정해진다
public record FriendKeyset(
        LocalDateTime updatedAt,
        UUID userId
) {

}
