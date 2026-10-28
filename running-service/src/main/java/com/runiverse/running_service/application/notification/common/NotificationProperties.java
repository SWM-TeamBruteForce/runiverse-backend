package com.runiverse.running_service.application.notification.common;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "notification")
@Validated
public record NotificationProperties(
        // 이 기간보다 오래된 알림은 목록·안 읽은 수에서 빠진다. 행을 지우는 주기는 운영이 따로 정한다
        @NotNull Duration retention
) {

    // null 검사는 @NotNull이 따로 보고한다 — 여기서 터지면 원인이 가려진다
    @AssertTrue(message = "알림 보관 기간은 0보다 커야 한다")
    public boolean isRetentionPositive() {
        return retention == null || retention.isPositive();
    }
}
