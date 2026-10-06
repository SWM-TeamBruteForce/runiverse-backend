package com.runiverse.running_service.infrastructure.redis.emailverification;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "email-verification")
@Validated
public record EmailVerificationProperties(
        @NotNull Duration codeTtl,
        @NotNull Duration cooldown,
        @NotNull Duration ticketTtl,
        @Positive int maxAttempts,
        @Positive int dailyLimit
) {

}
