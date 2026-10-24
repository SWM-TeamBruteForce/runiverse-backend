package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.relationship.UserRelationship;

import java.util.Optional;

// 두 사람이 서로를 본 행 한 쌍 — 행이 없으면 null(관계 없음)이다
public record UserRelationshipPair(
        UserRelationship mine,     // 나 → 상대
        UserRelationship theirs    // 상대 → 나
) {

    public Optional<UserRelationship> findMine() {
        return Optional.ofNullable(mine);
    }

    public Optional<UserRelationship> findTheirs() {
        return Optional.ofNullable(theirs);
    }
}
