package com.runiverse.running_service.infrastructure.mail;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "mail.ses")
@Validated
public record SesProperties(
        @NotBlank String region,
        @NotBlank String from,
        @NotBlank String fromName,
        String accessKeyId,      // 비우면 기본 자격증명 체인(IAM Role) 사용
        String secretAccessKey
) {

}
