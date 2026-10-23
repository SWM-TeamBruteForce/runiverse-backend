package com.runiverse.running_service.domain.relationship.vo;

import com.runiverse.running_service.domain.relationship.exception.InvalidRelationshipTransitionException;

import java.util.Set;

// 행 주인 관점의 관계 — 관계가 끝나도 행을 지우지 않고 NONE으로 되돌린다
public enum RelationshipType {
    NONE,
    FRIEND,
    FRIEND_REQUEST_SENT,
    FRIEND_REQUEST_RECEIVED,
    BLOCKED;

    public RelationshipType transitionTo(RelationshipType next) {
        if (!allowedNext().contains(next)) {
            throw new InvalidRelationshipTransitionException();
        }
        return next;
    }

    // 친구는 요청을 거쳐서만 된다 — NONE에서 바로 FRIEND로 가지 않는다
    private Set<RelationshipType> allowedNext() {
        return switch (this) {
            case NONE -> Set.of(FRIEND_REQUEST_SENT, FRIEND_REQUEST_RECEIVED, BLOCKED);
            case FRIEND_REQUEST_SENT, FRIEND_REQUEST_RECEIVED -> Set.of(FRIEND, NONE, BLOCKED);
            case FRIEND -> Set.of(NONE, BLOCKED);
            case BLOCKED -> Set.of(NONE);
        };
    }
}
