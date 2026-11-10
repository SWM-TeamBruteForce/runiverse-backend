package com.runiverse.running_service.presentation.app.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AppVersionRequest(
        @NotBlank(message = "플랫폼은 필수입니다.")
        @Pattern(
                regexp = "^(?i)(IOS|ANDROID|WATCH_IOS|WATCH_ANDROID)$",
                message = "플랫폼은 IOS, ANDROID, WATCH_IOS, WATCH_ANDROID 중 하나여야 합니다."
        )
        String platform
) {

}
