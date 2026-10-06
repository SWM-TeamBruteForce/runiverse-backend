package com.runiverse.running_service.infrastructure.sse;

import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.match.MatchRoomSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 명부 변화에 맞춰 방 채널 구독을 켜고 끈다 — 명부 자체는 MatchRoomMemberRegistry가 든다
@Component
@RequiredArgsConstructor
public class MatchRoomMembershipAdapter implements MatchRoomMembershipPort {

    private final MatchRoomMemberRegistry registry;
    private final MatchRoomSubscriber matchRoomSubscriber;

    @Override
    public void join(UserId userId, Long runningRoomId) {
        // 그 방의 첫 참가자를 받은 순간에만 구독한다
        registry.join(userId, runningRoomId).ifPresent(matchRoomSubscriber::subscribe);
    }

    @Override
    public void leave(UserId userId) {
        registry.leave(userId).ifPresent(matchRoomSubscriber::unsubscribe);
    }
}
