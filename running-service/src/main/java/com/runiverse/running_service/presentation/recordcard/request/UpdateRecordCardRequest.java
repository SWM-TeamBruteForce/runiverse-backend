package com.runiverse.running_service.presentation.recordcard.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// 9-3 — 부분 수정이다. 보내지 않은 필드는 그대로 둔다. images는 수정 뒤 사진 전체(순서대로) — 생략하면 사진·설명은 그대로다
public record UpdateRecordCardRequest(
        // 지울 수 없다 — 보내면 공백만 있는 값은 400(isMemoNotBlank)
        @Size(max = 1000, message = "회고 글은 1000자 이하여야 합니다.")
        String memo,

        @Pattern(regexp = "^(DAILY|TRAVEL|RACE)$", message = "활동 유형은 DAILY, TRAVEL, RACE 중 하나여야 합니다.")
        String activityType,

        @Pattern(regexp = "^(FRIENDS|PUBLIC|PRIVATE)$",
                message = "공개 범위는 FRIENDS, PUBLIC, PRIVATE 중 하나여야 합니다.")
        String visibility,

        @Size(max = 5, message = "사진은 5장 이하만 넣을 수 있습니다.")
        List<@NotNull(message = "입력값이 올바르지 않습니다.") @Valid Image> images,

        // images를 보냈으면 그 배열 기준, 생략했으면 지금 사진 기준(그 범위는 유스케이스가 본다)
        Integer thumbnailIndex
) {

    // 사진 한 자리 — 남길 사진(recordCardImageId) 또는 새 사진(mimeType·fileSizeBytes·thumbnailFileSizeBytes·fileName) 중 하나.
    // 규칙은 9-1과 같다 — 남길 사진은 작은 버전이 이미 있어 다시 올리지 않는다
    public record Image(
            UUID recordCardImageId,

            @Pattern(regexp = "^(?i)(image/jpeg|image/png|image/webp)$",
                    message = "이미지는 JPEG, PNG, WEBP 형식만 업로드할 수 있습니다.")
            String mimeType,

            @Min(value = 1, message = "파일 크기는 1바이트 이상이어야 합니다.")
            Long fileSizeBytes,

            @Min(value = 1, message = "썸네일 크기는 1바이트 이상이어야 합니다.")
            Long thumbnailFileSizeBytes,

            @Size(max = 100, message = "파일 이름은 100자 이하여야 합니다.")
            @Pattern(regexp = "^(?! *\\.{1,2} *$)[^/\\\\\\p{Cntrl}]+$", message = "파일 이름에 쓸 수 없는 문자가 있습니다.")
            String fileName,

            // 수정 뒤의 설명 — 남길 사진도 지금 설명을 다시 보낸다. 생략·빈 문자열·공백만이면 설명을 지운다
            @Size(max = 100, message = "사진 설명은 100자 이하여야 합니다.")
            String caption
    ) {

        // 남길 사진이면 새 사진 정보가 없고, 새 사진이면 형식·크기·이름이 모두 있다(썸네일 크기는 아래가 따로 본다)
        @AssertTrue(message = "사진 항목에는 기존 사진 ID와 새 사진 정보 중 하나만 넣어야 합니다.")
        public boolean isOneKind() {
            boolean anyNew = mimeType != null || fileSizeBytes != null || thumbnailFileSizeBytes != null
                    || fileName != null;
            boolean allNew = mimeType != null && fileSizeBytes != null && fileName != null && !fileName.isBlank();
            return recordCardImageId != null ? !anyNew : allNew;
        }

        // 새 사진은 작은 버전도 함께 올린다 — 9-1과 같이 필수다
        @AssertTrue(message = "썸네일 크기는 필수입니다.")
        public boolean isThumbnailGivenForNewImage() {
            return recordCardImageId != null || thumbnailFileSizeBytes != null;
        }
    }

    // 회고 글 VO와 같은 isBlank로 본다 — 정규식 \S는 ASCII 공백만 알아 전각 공백만 있는 값을 통과시킨다
    @AssertTrue(message = "회고 글은 비어 있을 수 없습니다.")
    public boolean isMemoNotBlank() {
        return memo == null || !memo.isBlank();
    }

    @AssertTrue(message = "같은 사진을 두 번 넣을 수 없습니다.")
    public boolean isNoDuplicateImage() {
        if (images == null) {
            return true;
        }
        Set<UUID> seen = new HashSet<>();
        return images.stream()
                .filter(Objects::nonNull)
                .map(Image::recordCardImageId)
                .filter(Objects::nonNull)
                .allMatch(seen::add);
    }

    // images를 보냈을 때만 여기서 본다 — 생략했으면 지금 사진 수를 알아야 해서 유스케이스가 본다
    @AssertTrue(message = "썸네일 위치가 사진 범위를 벗어났습니다.")
    public boolean isThumbnailIndexInRange() {
        if (thumbnailIndex == null || images == null) {
            return thumbnailIndex == null || thumbnailIndex >= 0;
        }
        return thumbnailIndex >= 0 && thumbnailIndex < images.size();
    }
}
