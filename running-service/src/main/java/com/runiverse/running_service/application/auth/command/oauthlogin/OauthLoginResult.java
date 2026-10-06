package com.runiverse.running_service.application.auth.command.oauthlogin;

import java.util.UUID;

public record OauthLoginResult(
        UUID userId,
        String accessToken,
        String refreshToken
) {

}
