package com.runiverse.running_service.presentation.match.sse;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "match-stream")
@Validated
public record MatchStreamProperties(
        @NotNull Duration timeout
) {

}
