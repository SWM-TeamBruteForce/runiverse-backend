package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,
        @Size(
                min = 6,
                max = 16,
                message = "비밀번호는 6자 이상 16자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}\\[\\]:;\"'<>,.?/\\\\|`~]).{6,16}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다."
        )
        @NotBlank(message = "비밀번호는 필수입니다.")
        String newPassword
) {

}
