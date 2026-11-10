package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.RecordCardMemoRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardMemoTooLongException;

import java.util.Optional;

// 카드 메모 — 메모가 없으면 이 값을 만들지 않는다(빈 문자열로 저장하지 않는다)
public record RecordCardMemo(String value) {

    private static final int MAX_LENGTH = 500;

    public RecordCardMemo {
        if (value == null || value.isBlank()) {
            throw new RecordCardMemoRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new RecordCardMemoTooLongException();
        }
    }

    // 요청의 메모를 그대로 받는다 — 없거나 비어 있으면 메모 없음
    public static Optional<RecordCardMemo> fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RecordCardMemo(value));
    }
}
