package com.runiverse.running_service.application.auth.command.refresh;

public record RefreshResult(
        String accessToken,
        String refreshToken
) {

}
