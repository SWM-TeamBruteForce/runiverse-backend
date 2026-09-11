package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.PublishRunningComboPort;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningComboUpdateRedisAdapter implements PublishRunningComboPort {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(Long runningRoomId, RunningComboUpdate update) {
        RunningRoomMessage envelope = new RunningRoomMessage(
                RunningRoomMessageType.COMBO, ComboMessage.of(runningRoomId, update));
        try {
            redisTemplate.convertAndSend(
                    RunningChannel.room(runningRoomId), jsonMapper.writeValueAsString(envelope));
        } catch (RuntimeException e) {
            // supersede와 달리 던지지 않는다 — 콤보는 화면 표시일 뿐이고
            // 다음 배치가 현재 상태를 통째로 다시 나른다
            log.warn("러닝 콤보 통지 발행 실패 — roomId={}, 수신자={}명",
                    runningRoomId, update.recipients().size(), e);
        }
    }
}
