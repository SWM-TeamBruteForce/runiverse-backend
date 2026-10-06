package com.runiverse.running_service.application.user.query.profile;

import java.math.BigDecimal;
import java.time.LocalDate;

// 온보딩 전이면 아래 네 값이 함께 null이다
public record GetMyProfileResult(
        String introduction,   // 없으면 null
        String gender,
        LocalDate birthday,
        BigDecimal weightKg,
        BigDecimal heightCm
) {

}
