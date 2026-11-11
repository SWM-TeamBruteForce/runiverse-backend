package com.runiverse.running_service.infrastructure.redis.match;

import com.runiverse.running_service.application.match.port.out.PublishMatchStreamSupersedePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchSupersedeRedisAdapter implements PublishMatchStreamSupersedePort {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(UserId userId, String winnerConnectionId) {
        MatchSupersedeMessage message = new MatchSupersedeMessage(userId.value(), winnerConnectionId);
        try {
            redisTemplate.convertAndSend(MatchChannel.SUPERSEDE, jsonMapper.writeValueAsString(message));
        } catch (RuntimeException e) {
            // 러닝과 달리 던지지 않는다 — 새 연결은 이미 붙어 스냅샷까지 받을 수 있다.
            // Redis가 닿지 않으면 다른 인스턴스로 가는 방 이벤트도 함께 멈추고,
            // 남은 옛 연결은 keep-alive 실패나 앱의 종료가 정리한다
            log.error("[매칭] 이전 스트림 종료 알림 발행 실패: Redis 오류 - userId={}, connectionId={}",
                    userId.value(), winnerConnectionId, e);
        }
    }
}
