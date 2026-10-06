package com.runiverse.running_service.domain.running.player.vo;

import com.runiverse.running_service.domain.running.player.exception.InvalidDesiredPlayerCountException;
import com.runiverse.running_service.domain.running.room.vo.PlayerCount;

public record DesiredPlayerCount(int value) {

    private static final int MIN = 2;       // 1명은 매칭이 아니라 솔로다
    private static final int DEFAULT = 4;   // 1차에는 입력 UI가 없어 항상 이 값이다

    public DesiredPlayerCount {
        if (value < MIN || value > PlayerCount.MAX_PLAYER) {
            throw new InvalidDesiredPlayerCountException();
        }
    }

    // 인원을 고르지 않은 신청 — 방은 4자리로 열린다
    public static DesiredPlayerCount defaultCount() {
        return new DesiredPlayerCount(DEFAULT);
    }
}
