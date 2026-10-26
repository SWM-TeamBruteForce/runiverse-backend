package com.runiverse.running_service.domain.device.vo;

import com.runiverse.running_service.domain.device.exception.PushTokenRequiredException;

// FCM 등록 토큰 — 공식 길이 상한이 없어 길이는 보지 않는다
public record PushToken(String value) {

    public PushToken {
        if (value == null || value.isBlank()) {
            throw new PushTokenRequiredException();
        }
    }
}
