package com.runiverse.running_service.presentation.user.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ProfileImageUploadUrlRequest(
        @NotBlank(message = "이미지 형식은 필수입니다.")
        @Pattern(
                regexp = "^(?i)(image/jpeg|image/png|image/webp)$",
                message = "이미지는 JPEG, PNG, WEBP 형식만 업로드할 수 있습니다."
        )
        String mimeType,

        // 서명에 그대로 들어가는 것으로 바이트 수를 올려야 한다.
        @NotNull(message = "파일 크기는 필수입니다.")
        @Min(value = 1, message = "파일 크기는 1바이트 이상이어야 합니다.")
        @Max(value = 10_485_760, message = "이미지는 10MB 이하만 업로드할 수 있습니다.")
        Long fileSizeBytes
) {

}
