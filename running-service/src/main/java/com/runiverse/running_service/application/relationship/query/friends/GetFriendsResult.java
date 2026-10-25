package com.runiverse.running_service.application.relationship.query.friends;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.util.List;
import java.util.UUID;

public record GetFriendsResult(List<Friend> items, String nextCursor) {   // 마지막 페이지면 nextCursor는 null

    public record Friend(
            UUID userId,
            String nickname,
            String profileImageUrl,               // 사진이 없으면 null
            RelationshipType relationshipType     // 조회자 기준 — 항목이 조회자 본인이면 null
    ) {

    }
}
