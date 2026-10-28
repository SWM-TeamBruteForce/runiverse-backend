package com.runiverse.running_service.presentation.user.response;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        boolean isMe,
        String nickname,
        String profileImageUrl,
        String introduction,
        long friendCount,
        RelationshipType relationshipType   // 조회자 기준 지금 관계. 본인 프로필이면 null
) {

}
