package com.runiverse.running_service.domain.user.vo;

public enum LoginType {
    LOCAL,
    GOOGLE,
    KAKAO;

    // Provider에는 LOCAL이 없다 — 소셜 연동이 없으면 로컬 계정이다
    public static LoginType from(Provider provider) {
        if (provider == null) {
            return LOCAL;
        }
        return valueOf(provider.name());
    }
}
