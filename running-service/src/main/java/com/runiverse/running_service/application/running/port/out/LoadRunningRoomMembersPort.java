package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.Set;

public interface LoadRunningRoomMembersPort {

    // 이 인스턴스가 들고 있는 그 방의 참가자 — 진행 정보를 밀어 넣을 대상이다.
    // 다른 서버에 붙은 참가자는 그쪽 인스턴스가 자기 몫을 보낸다
    Set<UserId> usersIn(Long runningRoomId);
}
