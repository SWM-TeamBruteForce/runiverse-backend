package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.MusicKeyRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.MusicKeyTooLongException;

// AI가 올린 음악의 S3 key — 서버가 정하지 않고, AI 서버가 완료 알림으로 알려 준 값을 그대로 담는다.
// 그래서 생성이 끝나(COMPLETED) 객체가 올라온 음악에만 있다
public record MusicKey(String value) {

    private static final int MAX_LENGTH = 255;

    public MusicKey {
        if (value == null) {
            throw new MusicKeyRequiredException();
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new MusicKeyRequiredException();
        }
        if (value.length() > MAX_LENGTH) {
            throw new MusicKeyTooLongException();
        }
    }
}
