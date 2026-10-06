package com.runiverse.running_service.application.auth.command.login;

import java.util.UUID;

public record LoginResult(
        UUID userId,
        String accessToken,
        String refreshToken
) {

}
