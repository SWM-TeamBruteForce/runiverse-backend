package com.runiverse.running_service.presentation.auth.response;

import java.util.UUID;

public record SignUpResponse(
        UUID userId,
        String accessToken,
        String refreshToken
) {

}
