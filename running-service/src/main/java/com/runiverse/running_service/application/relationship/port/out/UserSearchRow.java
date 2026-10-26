package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.util.UUID;

public record UserSearchRow(
        UUID userId,
        String nickname,                  // 커서를 만든다 — UNIQUE라 이것만으로 다음 위치가 정해진다
        String profileImageKey,           // 사진이 없으면 null
        RelationshipType viewerType       // 조회자 관점 — 조회자와 행이 없으면 null
) {

}
