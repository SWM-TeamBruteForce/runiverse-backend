package com.runiverse.running_service.application.user.command.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// 부분 수정 — null인 필드는 그대로 둔다. 소개글만 빈 문자열로 지울 수 있다
public record ChangeMyProfileCommand(
        UUID userId,
        String introduction,
        String gender,
        LocalDate birthday,
        BigDecimal weightKg,
        BigDecimal heightCm
) {

    // user_onboardings에 저장하는 값이 하나라도 담겼는지 — 온보딩 미완료를 가릴 기준이다
    public boolean hasOnboardingField() {
        return gender != null || birthday != null || weightKg != null || heightCm != null;
    }
}
