package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.ProviderNotSupportedException;
import com.runiverse.running_service.domain.user.exception.ProviderRequiredException;

import java.util.Arrays;
import java.util.Locale;

public enum Provider {
    KAKAO,
    GOOGLE;

    // 경로에 쓰는 소문자 이름과 정확히 같을 때만 인식한다 — GOOGLE·Kakao처럼 대소문자가 다르면 지원하지 않는 provider다.
    // 상수 이름은 DB 저장값·loginType 응답과 같아 대문자로 둔다
    public static Provider from(String value) {
        if (value == null || value.isBlank()) {
            throw new ProviderRequiredException();
        }
        return Arrays.stream(values())
                .filter(provider -> provider.name().toLowerCase(Locale.ROOT).equals(value))
                .findFirst()
                .orElseThrow(ProviderNotSupportedException::new);
    }
}
