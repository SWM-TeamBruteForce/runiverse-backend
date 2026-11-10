package com.runiverse.running_service.domain.common.vo;

import com.runiverse.running_service.domain.common.exception.PlatformRequiredException;
import com.runiverse.running_service.domain.common.exception.UnsupportedPlatformException;

import java.util.Locale;

// 워치 값은 출시 채널로 가른다 — 애플워치는 iOS 앱과, 갤럭시워치는 안드로이드 앱과 같은 스토어로 나간다.
// 워치는 서버에 직접 붙지 않아 앱 버전 기준에만 쓰고, 기기 등록은 요청 검증·DB 제약이 폰 값만 받는다
public enum Platform {
    IOS,
    ANDROID,
    WATCH_IOS,
    WATCH_ANDROID;

    public static Platform from(String value) {
        if (value == null) {
            throw new PlatformRequiredException();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new PlatformRequiredException();
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedPlatformException();
        }
    }
}
