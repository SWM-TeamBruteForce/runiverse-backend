package com.runiverse.running_service.application.recordcard.command.update;

import java.util.List;
import java.util.UUID;

// 9-3 — 보낸 필드만 바꾼다(null이면 그대로). images가 null이면 사진·설명은 그대로, 빈 목록이면 사진을 모두 뺀다
public record UpdateRecordCardCommand(
        UUID userId,
        UUID recordCardId,
        String memo,
        String activityType,
        String visibility,
        List<Image> images,         // 수정 뒤 사진 전체, 캐러셀 순서 — null이면 사진을 바꾸지 않는다
        Integer thumbnailIndex
) {

    public UpdateRecordCardCommand {
        images = images == null ? null : List.copyOf(images);
    }

    // 사진 한 자리 — 남길 사진(recordCardImageId)이거나 새로 올릴 사진(형식·크기·썸네일 크기·이름). caption은 수정 뒤의 설명이다
    public record Image(UUID recordCardImageId, String mimeType, Long fileSizeBytes, Long thumbnailFileSizeBytes,
                        String fileName, String caption) {

        public Image {
            // 9-1과 같다 — 앞뒤 공백을 지운 이름이 사진 key의 마지막 칸이 된다
            fileName = fileName == null ? null : fileName.trim();
        }

        public boolean isNew() {
            return recordCardImageId == null;
        }
    }
}
