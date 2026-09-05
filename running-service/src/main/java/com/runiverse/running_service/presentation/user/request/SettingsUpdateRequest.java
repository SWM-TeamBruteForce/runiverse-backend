package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.Pattern;

// 부분 수정이라 전부 선택이다. alertConsent가 래퍼인 이유는 생략과 false를 갈라야 해서다
public record SettingsUpdateRequest(
        Boolean alertConsent,

        @Pattern(
                regexp = "^(?i)(FRIENDS|PUBLIC)$",
                message = "프로필 공개 범위는 FRIENDS 또는 PUBLIC이어야 합니다."
        )
        String profileVisibility
) {

}
