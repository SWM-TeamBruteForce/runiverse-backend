package com.runiverse.running_service.infrastructure.redis.scheduling;

import com.runiverse.running_service.application.scheduling.port.out.PublishScheduledJobPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobRedisAdapter implements PublishScheduledJobPort {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(ScheduledJob job) {
        JobTarget target = job.getTarget();
        Long scheduledJobId = job.getScheduledJobId().orElseThrow().value();
        ScheduledJobMessage message = new ScheduledJobMessage(
                scheduledJobId, target.type(), target.id(), job.getExecuteAt());
        try {
            redisTemplate.convertAndSend(
                    ScheduleChannel.JOB, jsonMapper.writeValueAsString(message));
        } catch (RuntimeException e) {
            // 던지지 않는다 — 이미 커밋된 뒤라 되돌릴 것이 없고,
            // 내 타이머는 이미 걸려 있어 최소 한 대는 깬다.
            // 정본은 DB라 다른 인스턴스도 다음 부팅에 되살린다
            log.error("[예약] 예약 전파 실패: Redis 오류 - scheduledJobId={}", scheduledJobId, e);
        }
    }
}
