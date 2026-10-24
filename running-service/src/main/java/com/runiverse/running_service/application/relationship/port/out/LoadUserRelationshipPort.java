package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface LoadUserRelationshipPort {

    // 두 행을 함께 잠가 읽는다 — 짝을 바꾸는 동안 상대의 취소·거절·수락이 끼어들지 못하게 한다
    UserRelationshipPair loadPairForUpdate(UserId userId, UserId targetId);
}
