package com.runiverse.running_service.application.relationship.command.request;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

public record RequestFriendResult(
        RelationshipType relationshipType    // 요청 뒤 내 관계 — FRIEND_REQUEST_SENT, 상대가 먼저 요청해 뒀으면 FRIEND
) {

}
