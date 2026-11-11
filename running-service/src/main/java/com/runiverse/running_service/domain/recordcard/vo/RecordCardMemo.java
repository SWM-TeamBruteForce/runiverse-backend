package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.RecordCardMemoRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.RecordCardMemoTooLongException;

// 카드의 회고 글 — 음악 생성이 글에 드러난 감정·여운을 읽으므로 비어 있을 수 없다
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
}
