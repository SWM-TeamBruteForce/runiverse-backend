package com.runiverse.running_service.application.auth.command.signup;

import java.util.UUID;

public record SignUpResult(
        UUID userId,
        String accessToken,
        String refreshToken
) {

}
