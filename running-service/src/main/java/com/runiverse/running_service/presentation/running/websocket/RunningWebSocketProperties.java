package com.runiverse.running_service.presentation.running.websocket;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "running-websocket")
@Validated
public record RunningWebSocketProperties(
        @NotNull Duration sendTimeLimit,
        @NotNull DataSize sendBufferSizeLimit
) {

}
