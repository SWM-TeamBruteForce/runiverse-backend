package com.runiverse.running_service.application.common.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface CountFriendsPort {

    // 이 사람의 친구 수 — 친구는 대칭이라 이 사람이 주인인 FRIEND 행만 센다
    long countFriends(UserId userId);
}
