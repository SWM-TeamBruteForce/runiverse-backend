package com.runiverse.running_service.infrastructure.websocket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "websocket")
@Validated
public record WebSocketProperties(
        @NotNull Duration idleTimeout,
        @NotNull DataSize maxTextMessageBufferSize,
        @NotBlank String runningEndpoint
) {

}
