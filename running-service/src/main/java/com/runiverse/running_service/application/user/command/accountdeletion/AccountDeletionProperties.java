package com.runiverse.running_service.application.user.command.accountdeletion;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "account-deletion")
@Validated
public record AccountDeletionProperties(
        // 신고 대응 목적으로 탈퇴 기록을 들고 있는 기간
        @NotNull Duration retention
) {
}
