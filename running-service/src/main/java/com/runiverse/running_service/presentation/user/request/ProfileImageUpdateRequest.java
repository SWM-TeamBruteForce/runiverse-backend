package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileImageUpdateRequest(
        @NotBlank(message = "프로필 이미지 키는 필수입니다.")
        @Size(max = 255, message = "프로필 이미지 키는 255자 이하여야 합니다.")
        String profileImageKey
) {

}
