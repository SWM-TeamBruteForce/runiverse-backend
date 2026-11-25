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

    // 올릴 사진 하나 — 아직 파일은 없고 형식·크기·이름만 안다. 이름은 사용자가 올린 파일 이름에서 앞뒤 공백만 지운 값이다.
    // 작은 버전(썸네일)은 원본과 같은 형식이라 크기만 받는다
    public record Image(String mimeType, long fileSizeBytes, long thumbnailFileSizeBytes, String fileName,
                        String caption) {

        public Image {
            // 이 이름이 사진 key의 마지막 칸이 된다 — 업로드 URL 서명·S3 확인·DB 저장이 모두 이 값을 쓴다.
            // 사진 key VO가 trim()으로 다듬으므로 같은 규칙으로 여기서 먼저 지운다. 지우지 않으면 S3 key와 DB key가 갈린다
            fileName = fileName == null ? null : fileName.trim();
        }
    }
}
