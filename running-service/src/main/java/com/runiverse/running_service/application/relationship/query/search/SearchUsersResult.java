package com.runiverse.running_service.application.relationship.query.search;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.util.List;
import java.util.UUID;

public record SearchUsersResult(List<User> items, String nextCursor) {   // 마지막 페이지면 nextCursor는 null

    public record User(
            UUID userId,
            String nickname,
            String profileImageUrl,               // 사진이 없으면 null
            RelationshipType relationshipType     // 조회자 기준 — 본인은 결과에 없어 늘 값이 있다
    ) {

    }
}
