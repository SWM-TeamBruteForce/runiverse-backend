package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.PublishRunningProgressPort;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningProgressRedisAdapter implements PublishRunningProgressPort {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(Long runningRoomId, RunningProgress progress) {
        RunningRoomMessage envelope = new RunningRoomMessage(
                RunningRoomMessageType.PROGRESS, ProgressMessage.of(runningRoomId, progress));
        try {
            redisTemplate.convertAndSend(
                    RunningChannel.room(runningRoomId), jsonMapper.writeValueAsString(envelope));
        } catch (RuntimeException e) {
            // supersede와 달리 던지지 않는다 — 이건 남의 화면에 뜨는 표시일 뿐이고
            // 10초 뒤 다음 배치가 최신값을 다시 나른다. 좌표는 이미 저장돼 있다
            log.warn("러닝 진행 통지 발행 실패 — roomId={}, userId={}",
                    runningRoomId, progress.userId(), e);
        }
    }
}
