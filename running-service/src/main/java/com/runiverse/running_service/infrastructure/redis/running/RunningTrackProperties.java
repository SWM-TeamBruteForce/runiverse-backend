package com.runiverse.running_service.infrastructure.redis.running;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "running-track")
@Validated
public record RunningTrackProperties(@NotNull Duration ttl) {

}
