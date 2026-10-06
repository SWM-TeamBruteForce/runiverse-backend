package com.runiverse.running_service.infrastructure.security;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "cors")
@Validated
public record CorsProperties(
        @NotEmpty List<String> allowedOrigins,
        @NotEmpty List<String> allowedMethods,
        @NotEmpty List<String> allowedHeaders
) {

}
