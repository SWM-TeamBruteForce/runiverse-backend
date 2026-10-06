package com.runiverse.running_service.infrastructure.websocket;

import com.runiverse.running_service.application.running.port.out.RunningRoomMembershipPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.running.RunningRoomSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 명부 변화에 맞춰 방 채널 구독을 켜고 끈다 — 명부 자체는 RunningRoomMemberRegistry가 든다
@Component
@RequiredArgsConstructor
public class RunningRoomMembershipAdapter implements RunningRoomMembershipPort {

    private final RunningRoomMemberRegistry registry;
    private final RunningRoomSubscriber runningRoomSubscriber;

    @Override
    public void join(UserId userId, Long runningRoomId) {
        // 방을 갈아타면 이전 방 구독부터 끊는다 — 같은 방 재연결은 건드리지 않는다
        registry.roomOf(userId)
                .filter(previous -> !previous.equals(runningRoomId))
                .flatMap(previous -> registry.leave(userId))
                .ifPresent(runningRoomSubscriber::unsubscribe);
        // 그 방의 첫 참가자를 받은 순간에만 구독한다
        registry.join(userId, runningRoomId).ifPresent(roomId -> {
            try {
                runningRoomSubscriber.subscribe(roomId);
            } catch (RuntimeException e) {
                // 구독 없이 명부만 남으면 재시도가 첫 참가자가 아니게 돼 구독을 영영 건너뛴다.
                // 정식 퇴장으로 되돌린다 — 반쯤 걸린 구독이 있어도 해제까지 정리되고, 없던 구독의 해제는 무해하다
                leave(userId);
                throw e;
            }
        });
    }

    @Override
    public void leave(UserId userId) {
        registry.leave(userId).ifPresent(runningRoomSubscriber::unsubscribe);
    }
}
