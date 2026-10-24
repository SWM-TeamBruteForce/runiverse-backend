package com.runiverse.running_service.application.relationship.command.accept;

import com.runiverse.running_service.domain.relationship.vo.RelationshipType;

public record AcceptFriendRequestResult(
        RelationshipType relationshipType   // 수락 뒤 내 관계 — 늘 FRIEND
) {

}
