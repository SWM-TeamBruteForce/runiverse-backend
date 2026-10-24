package com.runiverse.running_service.application.relationship.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface EnsureUserRelationshipPairPort {

    // 두 행이 없으면 NONE으로 만들고 있으면 그대로 둔다 — 행이 있어야 loadPairForUpdate로 잠글 수 있다.
    // 처음 맺는 쌍에 동시에 요청이 들어와도 UNIQUE 위반 없이 한쪽이 기다렸다 지나간다
    void ensurePair(UserId userId, UserId targetId);
}
