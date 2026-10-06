package com.runiverse.running_service.domain.running.room.vo;

import com.runiverse.running_service.domain.running.room.exception.InvalidLeaveCountException;

public record LeaveCount(int value) {

    private static final int MIN = 0;

    public LeaveCount {
        if (value < MIN) {
            throw new InvalidLeaveCountException();
        }
    }

    public static LeaveCount zero() {
        return new LeaveCount(MIN);
    }

    // 연결이 끊길 때마다 하나씩 — 페널티 판정 근거가 된다
    public LeaveCount increase() {
        return new LeaveCount(value + 1);
    }
}
