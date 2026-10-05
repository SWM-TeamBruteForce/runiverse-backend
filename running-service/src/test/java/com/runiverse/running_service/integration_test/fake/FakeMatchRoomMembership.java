package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.match.port.out.MatchRoomMembershipPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.sse.MatchRoomMemberRegistry;

import java.util.HashSet;
import java.util.Set;

// MatchRoomMembershipAdapter를 대신한다 — 실제 레지스트리를 쓰고 Redis 구독만 방 목록으로 남긴다
public class FakeMatchRoomMembership implements MatchRoomMembershipPort {

    private final MatchRoomMemberRegistry registry;
    private final Set<Long> subscribedRooms = new HashSet<>();

    public FakeMatchRoomMembership(MatchRoomMemberRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void join(UserId userId, Long runningRoomId) {
        registry.join(userId, runningRoomId).ifPresent(subscribedRooms::add);
    }

    @Override
    public void leave(UserId userId) {
        registry.leave(userId).ifPresent(subscribedRooms::remove);
    }

    // 검증 전용
    public boolean isSubscribed(Long runningRoomId) {
        return subscribedRooms.contains(runningRoomId);
    }
}
