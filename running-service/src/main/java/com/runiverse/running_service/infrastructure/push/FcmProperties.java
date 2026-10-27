package com.runiverse.running_service.infrastructure.push;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 서비스 계정 JSON을 base64 한 줄로 받는다 — 배포 env 파일이 여러 줄 값을 받지 않는다.
// local 프로필은 발송하지 않아 키를 해석하지 않으므로 아무 값이나 넣어도 된다
@ConfigurationProperties(prefix = "push.fcm")
@Validated
public record FcmProperties(
        @NotBlank String serviceAccountBase64
) {

}
