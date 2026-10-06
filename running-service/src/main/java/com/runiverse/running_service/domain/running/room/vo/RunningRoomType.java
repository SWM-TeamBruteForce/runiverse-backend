package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.RunningRoomTypeRequiredException;
import com.runiverse.running_service.domain.running.room.exception.UnsupportedRunningRoomTypeException;

import java.util.Locale;

public enum RunningRoomType {
    SOLO, // 혼자 러닝
    MATCH, // 매칭 러닝
    INVITE; // 초대 러닝

    // 문자열 → 종류. 클라이언트 오타가 조용히 다른 종류로 저장되지 않게 막는다
    public static RunningRoomType from(String value) {
        if (value == null) {
            throw new RunningRoomTypeRequiredException();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new RunningRoomTypeRequiredException();
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedRunningRoomTypeException();
        }
    }

    // 솔로는 모집 단계가 없어 확정(MATCHED)된 채로 태어난다 —
    // 태어나는 지점만 다르고 채널 입장 이후 흐름은 매칭과 같은 길을 탄다
    public RunningRoomStatus initialStatus() {
        return this == SOLO ? RunningRoomStatus.MATCHED : RunningRoomStatus.MATCHING;
    }

    // 모집 마감(start_at - 오프셋)·후보 스캔 대상인지
    public boolean isMatchable() {
        return this == MATCH;
    }
}
