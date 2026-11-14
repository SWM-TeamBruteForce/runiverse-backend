package com.runiverse.running_service.presentation.recordcard.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRecordCardDraftRequest(
        @NotNull(message = "러닝 기록 ID는 필수입니다.")
        @Positive(message = "입력값이 올바르지 않습니다.")
        Long runningRecordId,

        @NotBlank(message = "활동 유형은 필수입니다.")
        @Pattern(regexp = "^(DAILY|TRAVEL|RACE)$", message = "활동 유형은 DAILY, TRAVEL, RACE 중 하나여야 합니다.")
        String activityType,

        @NotBlank(message = "회고 글은 필수입니다.")
        @Size(max = 500, message = "회고 글은 500자 이하여야 합니다.")
        String memo,

        @NotBlank(message = "공개 범위는 필수입니다.")
        @Pattern(regexp = "^(FRIENDS|PUBLIC|PRIVATE)$",
                message = "공개 범위는 FRIENDS, PUBLIC, PRIVATE 중 하나여야 합니다.")
        String visibility,

        // 생략하면 빈 배열과 같다 — 배열 순서가 캐러셀 순서다
        @Size(max = 10, message = "사진은 10장 이하만 넣을 수 있습니다.")
        List<@NotNull(message = "입력값이 올바르지 않습니다.") @Valid Image> images,

        // 생략하면 첫 사진 — 사진이 없으면 보내지 않는다
        Integer thumbnailIndex
) {

    // 최대 크기(100MB)는 운영값이라 여기서 보지 않는다 — 유스케이스가 설정값으로 본다
    public record Image(
            @NotBlank(message = "이미지 형식은 필수입니다.")
            @Pattern(regexp = "^(?i)(image/jpeg|image/png|image/webp)$",
                    message = "이미지는 JPEG, PNG, WEBP 형식만 업로드할 수 있습니다.")
            String mimeType,

            @NotNull(message = "파일 크기는 필수입니다.")
            @Min(value = 1, message = "파일 크기는 1바이트 이상이어야 합니다.")
            Long fileSizeBytes,

            // 사진 key의 마지막 부분에 그대로 들어간다 — 폴더를 벗어나는 / · \ · 제어 문자와 . · ..만인 이름을 막는다
            @NotBlank(message = "파일 이름은 필수입니다.")
            @Size(max = 100, message = "파일 이름은 100자 이하여야 합니다.")
            @Pattern(regexp = "^(?!\\.{1,2}$)[^/\\\\\\p{Cntrl}]+$", message = "파일 이름에 쓸 수 없는 문자가 있습니다.")
            String fileName,

            // 생략·빈 문자열·공백만이면 설명 없음(400이 아니다)
            @Size(max = 100, message = "사진 설명은 100자 이하여야 합니다.")
            String caption
    ) {

    }

    // 사진이 없는데 보냈거나, 사진 범위를 벗어났다. 사진 수 초과는 @Size가 따로 보고한다
    @AssertTrue(message = "썸네일 위치가 사진 범위를 벗어났습니다.")
    public boolean isThumbnailIndexInRange() {
        if (thumbnailIndex == null) {
            return true;
        }
        int imageCount = images == null ? 0 : images.size();
        return thumbnailIndex >= 0 && thumbnailIndex < imageCount;
    }
}
