package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.time.LocalDateTime;
import java.util.UUID;

public record FriendRow(
        UUID userId,
        String nickname,
        String profileImageKey,           // 사진이 없으면 null
        RelationshipType type,            // 목록 주인 관점 — 묶음을 가른다
        LocalDateTime updatedAt,          // 커서를 만든다
        RelationshipType viewerType       // 조회자 관점 — 조회자와 행이 없으면 null
) {

}
