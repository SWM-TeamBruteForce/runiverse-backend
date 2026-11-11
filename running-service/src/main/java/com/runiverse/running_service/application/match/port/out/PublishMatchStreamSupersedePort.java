package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface PublishMatchStreamSupersedePort {

    // 이 사용자의 매칭 스트림은 winnerConnectionId 하나라고 모든 인스턴스에 알린다.
    // 방 채널이 아니라 공용 채널로 보낸다 — 방을 옮기면 옛 연결을 든 인스턴스는 새 방을 구독하지 않는다
    void publish(UserId userId, String winnerConnectionId);
}
