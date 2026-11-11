package com.runiverse.running_service.infrastructure.redis.match;

import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.PublishMatchEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEventRedisAdapter implements PublishMatchEventPort {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(MatchStreamEvent event) {
        MatchEventMessage message = new MatchEventMessage(
                event.type(), event.runningRoomId(), event.room(), event.ready(),
                event.chat(),
                event.excludedUserId() == null ? null : event.excludedUserId().value());
        try {
            redisTemplate.convertAndSend(
                    MatchChannel.room(event.runningRoomId()),
                    jsonMapper.writeValueAsString(message));
        } catch (RuntimeException e) {
            // 던지지 않는다 — 이미 커밋된 뒤라 되돌릴 것이 없다.
            // 방 상태 이벤트는 전체 상태라 다음 갱신이나 재연결 스냅샷이 복구하고,
            // 채팅은 변경분이라 받는 쪽이 다음 입장 때 목록(5-E-1)으로 맞춘다
            log.error("[매칭] 이벤트 발행 실패: Redis 오류 - type={}, roomId={}",
                    event.type(), event.runningRoomId(), e);
        }
    }
}
