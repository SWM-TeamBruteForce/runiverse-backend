package com.runiverse.running_service.presentation.device.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank(message = "입력값이 올바르지 않습니다.")
        @Size(max = 100, message = "입력값이 올바르지 않습니다.")
        String deviceId,
        // 토큰 규칙이 아니라 비정상 입력 차단용 상한이다 — FCM 토큰은 공식 길이 상한이 없다
        @NotBlank(message = "입력값이 올바르지 않습니다.")
        @Size(max = 4096, message = "입력값이 올바르지 않습니다.")
        String pushToken
) {

}
