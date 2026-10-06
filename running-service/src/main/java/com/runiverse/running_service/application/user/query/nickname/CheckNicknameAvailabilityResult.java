package com.runiverse.running_service.application.user.query.nickname;

public record CheckNicknameAvailabilityResult(
        String nickname,
        boolean available
) {

}
