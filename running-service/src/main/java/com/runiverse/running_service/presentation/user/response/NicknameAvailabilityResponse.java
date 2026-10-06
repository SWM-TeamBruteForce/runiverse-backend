package com.runiverse.running_service.presentation.user.response;

public record NicknameAvailabilityResponse(
        String nickname,
        boolean available
) {

}
