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
        // 방을 갈아타면 이전 방부터 뺀다 — 옛 연결의 종료 처리는 새 연결 보호 가드에 걸려 leave()를 건너뛴다.
        // 같은 방 재연결은 건드리지 않는다(끊었다 다시 구독하면 그 틈의 이벤트를 놓친다)
        registry.roomOf(userId)
                .filter(previous -> !previous.equals(runningRoomId))
                .flatMap(previous -> registry.leave(userId))
                .ifPresent(matchRoomSubscriber::unsubscribe);
        // 그 방의 첫 참가자를 받은 순간에만 구독한다
        registry.join(userId, runningRoomId).ifPresent(roomId -> {
            try {
                matchRoomSubscriber.subscribe(roomId);
            } catch (RuntimeException e) {
                // 구독 없이 명부만 남으면 재연결이 첫 참가자가 아니게 돼 구독을 영영 건너뛴다.
                // 그래서 명부에서 다시 빼고, 방이 비면 구독도 해제한다 — 반쯤 걸린 구독이 있어도 정리되고, 없던 구독의 해제는 무해하다
                leave(userId);
                throw e;
            }
        });
    }

    @Override
    public void leave(UserId userId) {
        registry.leave(userId).ifPresent(matchRoomSubscriber::unsubscribe);
    }
}
