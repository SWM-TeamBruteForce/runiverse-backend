package com.runiverse.running_service.application.user.query.profile;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.util.UUID;

public record GetUserProfileResult(
        UUID userId,
        boolean isMe,
        String nickname,          // 온보딩 전이면 null
        String profileImageUrl,   // 없으면 null
        String introduction,      // 없으면 null
        long friendCount,
        RelationshipType relationshipType   // 조회자 기준 지금 관계. 본인 프로필이면 null
) {

}
