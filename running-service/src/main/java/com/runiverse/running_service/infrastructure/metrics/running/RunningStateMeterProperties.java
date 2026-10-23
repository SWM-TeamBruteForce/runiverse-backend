package com.runiverse.running_service.infrastructure.metrics.running;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "running-state")
@Validated
public record RunningStateMeterProperties(
        // 이보다 오래 걸린 접근은 WARN으로 남긴다 — 배치 하나가 상태 저장소를 열 번 가까이 오가므로
        // 한 번이 이만큼 걸리면 진행 표시가 밀리기 시작한다
        @NotNull Duration slowThreshold
) {

}
