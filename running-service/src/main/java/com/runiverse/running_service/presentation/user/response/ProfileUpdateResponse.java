package com.runiverse.running_service.presentation.user.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileUpdateResponse(
        String introduction,
        String gender,
        LocalDate birthday,
        BigDecimal weightKg,
        BigDecimal heightCm
) {

}
