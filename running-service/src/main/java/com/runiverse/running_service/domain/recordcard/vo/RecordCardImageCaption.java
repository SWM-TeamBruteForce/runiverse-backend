package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageCaptionEmptyException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardImageCaptionTooLongException;

import java.util.Optional;

// 사진에 붙이는 한 줄 설명 — 선택 입력이다. 없으면 이 값 자체가 없고, 빈 문자열을 설명으로 두지 않는다.
// AI가 사진만으로 장면을 알기 어려울 때 사진 음악의 재료로 쓴다
public record RecordCardImageCaption(String value) {

    private static final int MAX_LENGTH = 100;

    public RecordCardImageCaption {
        if (value == null || value.isBlank()) {
            throw new RecordCardImageCaptionEmptyException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new RecordCardImageCaptionTooLongException();
        }
    }

    // 입력·저장값을 받는 입구 — 비웠으면(null·공백만) 설명 없음이다
    public static Optional<RecordCardImageCaption> ofNullable(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RecordCardImageCaption(value));
    }
}
