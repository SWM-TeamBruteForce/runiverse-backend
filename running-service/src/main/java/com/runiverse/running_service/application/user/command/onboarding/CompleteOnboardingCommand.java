package com.runiverse.running_service.application.user.command.onboarding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CompleteOnboardingCommand(
        UUID userId,
        String nickname,
        String gender,
        LocalDate birthday,
        int avgPace,
        BigDecimal weight,
        BigDecimal height
) {

}
