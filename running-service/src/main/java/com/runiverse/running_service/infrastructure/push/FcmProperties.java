package com.runiverse.running_service.infrastructure.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 서비스 계정 JSON을 base64 한 줄로 받는다 — 배포 env 파일이 여러 줄 값을 받지 않는다.
// local 프로필은 발송하지 않아 비어 있어도 되고, 그 밖에서 비면 FirebaseConfig가 기동을 멈춘다
@ConfigurationProperties(prefix = "push.fcm")
public record FcmProperties(
        String serviceAccountBase64
) {

}
