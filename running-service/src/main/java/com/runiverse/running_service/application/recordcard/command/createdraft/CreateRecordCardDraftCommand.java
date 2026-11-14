package com.runiverse.running_service.application.recordcard.command.createdraft;

import java.util.List;
import java.util.UUID;

public record CreateRecordCardDraftCommand(
        UUID userId,
        Long runningRecordId,
        String activityType,
        String memo,
        String visibility,
        List<Image> images,         // 캐러셀 순서대로 — 비어 있으면 지도+기본곡만
        Integer thumbnailIndex      // 생략하면 null — 사진이 있으면 핸들러가 0(첫 사진)으로 채운다
) {

    public CreateRecordCardDraftCommand {
        images = images == null ? List.of() : List.copyOf(images);
    }

    // 올릴 사진 하나 — 아직 파일은 없고 형식·크기·이름만 안다. 이름은 사용자가 올린 파일 이름 그대로다
    public record Image(String mimeType, long fileSizeBytes, String fileName, String caption) {

    }
}
