package com.runiverse.running_service.presentation.auth.response;

public record RefreshResponse(
        String accessToken,
        String refreshToken
) {

}
