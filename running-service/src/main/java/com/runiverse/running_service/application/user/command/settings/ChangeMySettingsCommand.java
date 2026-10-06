package com.runiverse.running_service.application.user.command.settings;

import java.util.UUID;

// 부분 수정 — null인 필드는 그대로 둔다. alertConsent가 래퍼인 이유는 생략과 false를 갈라야 해서다
public record ChangeMySettingsCommand(
        UUID userId,
        Boolean alertConsent,
        String profileVisibility
) {

}
