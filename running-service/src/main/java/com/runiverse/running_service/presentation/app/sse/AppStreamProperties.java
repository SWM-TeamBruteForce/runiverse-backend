package com.runiverse.running_service.presentation.app.sse;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app-stream")
@Validated
public record AppStreamProperties(
        @NotNull Duration timeout
) {

}
