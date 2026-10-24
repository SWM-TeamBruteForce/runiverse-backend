package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.relationship.UserRelationship;

public interface SaveUserRelationshipPort {

    // 새 행이면 만들고 있던 행이면 type을 갱신한다 — 짝을 이루는 두 행은 같은 트랜잭션에서 각각 저장한다
    void save(UserRelationship relationship);
}
