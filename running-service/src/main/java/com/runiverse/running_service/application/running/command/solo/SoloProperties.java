package com.runiverse.running_service.application.running.command.solo;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "solo")
@Validated
public record SoloProperties(
        // 강제 종료 = start_at + 이 값. 앱이 방 번호를 잃어 종료 메시지가 영영 오지 않으면
        // 활성 신청이 남아 다음 러닝이 막힌다. 러닝 트랙 TTL보다 짧아야 남은 좌표로 기록을 낸다
        @NotNull Duration forceFinishOffset
) {

}
