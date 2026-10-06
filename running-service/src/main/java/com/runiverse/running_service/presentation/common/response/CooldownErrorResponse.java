package com.runiverse.running_service.presentation.common.response;

import java.time.LocalDateTime;

public record CooldownErrorResponse(
        String code,
        String message,
        LocalDateTime cooldownUntil
) {

}
