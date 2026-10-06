package com.runiverse.running_service.infrastructure.redis.scheduling;

import com.runiverse.running_service.application.scheduling.port.out.RegisterJobTimerPort;
import com.runiverse.running_service.domain.scheduling.ScheduledJob;
import com.runiverse.running_service.domain.scheduling.vo.JobTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobListener implements MessageListener {

    private final JsonMapper jsonMapper;
    private final RegisterJobTimerPort registerJobTimerPort;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        ScheduledJobMessage payload;
        try {
            payload = jsonMapper.readValue(message.getBody(), ScheduledJobMessage.class);
        } catch (JacksonException e) {
            log.error("[예약] 예약 전파 메시지 파싱 실패: 메시지 형식 불일치 - channel={}",
                    new String(message.getChannel(), StandardCharsets.UTF_8), e);
            return;
        }
        // 발행한 본인도 자기 메시지를 받는다 — 어댑터가 jobId로 중복을 걸러낸다
        registerJobTimerPort.register(ScheduledJob.builder()
                .scheduledJobId(payload.scheduledJobId())
                .target(new JobTarget(payload.type(), payload.targetId()))
                .executeAt(payload.executeAt())
                .build());
    }
}
