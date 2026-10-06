package com.runiverse.running_service.application.user.command.profile;

import java.math.BigDecimal;
import java.time.LocalDate;

// 보낸 필드만 담아 돌려준다 — 바꾸지 않은 값은 null이다
public record ChangeMyProfileResult(
        String introduction,
        String gender,
        LocalDate birthday,
        BigDecimal weightKg,
        BigDecimal heightCm
) {

}
