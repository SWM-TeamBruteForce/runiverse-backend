package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface CheckFriendPort {

    // userId와 targetId가 친구(FRIEND)인가 — 친구는 대칭이라 내 행만 보면 된다.
    // 잠그지 않는다 — 보낸 뒤에 관계가 바뀌어도 초대는 이미 사건이라 되돌리지 않는다
    boolean isFriend(UserId userId, UserId targetId);
}
