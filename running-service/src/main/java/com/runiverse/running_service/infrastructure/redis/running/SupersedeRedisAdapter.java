package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.exception.RunningSessionUnavailableException;
import com.runiverse.running_service.application.running.port.out.PublishSupersedePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupersedeRedisAdapter implements PublishSupersedePort {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(UUID userId, Long runningRoomId, String winnerSessionId) {
        RunningRoomMessage envelope = new RunningRoomMessage(
                RunningRoomMessageType.SUPERSEDE, new SupersedeMessage(userId, winnerSessionId));
        try {
            redisTemplate.convertAndSend(
                    RunningChannel.room(runningRoomId), jsonMapper.writeValueAsString(envelope));
        } catch (RuntimeException e) {
            // Redis가 닿지 않으면 좌표 저장(RunningTrackRedisAdapter)도 못 한다.
            // 시작시켜봐야 기록이 통째로 유실되므로 여기서 끊고 클라의 재시도를 기다린다
            log.error("[러닝] 이전 세션 종료 통지 발행 실패: Redis 오류 - roomId={}, userId={}", runningRoomId, userId, e);
            throw new RunningSessionUnavailableException();
        }
    }
}
