package com.runiverse.running_service.domain.recordcard.vo;

import com.runiverse.running_service.domain.recordcard.exception.MusicKeyRequiredException;
import com.runiverse.running_service.domain.recordcard.exception.MusicKeyTooLongException;

// AI가 음악을 올릴 S3 key — 서버가 정해 생성 요청에 실어 보내고, 완료 알림도 이 값으로 돌아온다.
// 값이 있어도 생성이 끝나기 전에는 저장소에 객체가 없다
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
