package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface MatchRoomMembershipPort {

    // 스트림이 붙고 떨어질 때 방 채널 구독을 켜고 끈다
    void join(UserId userId, Long runningRoomId);

    void leave(UserId userId);
}
