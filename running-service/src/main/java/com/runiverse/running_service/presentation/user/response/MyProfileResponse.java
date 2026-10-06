package com.runiverse.running_service.presentation.user.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MyProfileResponse(
        String introduction,
        String gender,
        LocalDate birthday,
        BigDecimal weightKg,
        BigDecimal heightCm
) {

}
