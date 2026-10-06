package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 16,
                message = "닉네임은 2자 이상 16자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^[가-힣a-zA-Z0-9_]+$",
                message = "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다."
        )
        String nickname
) {

}
