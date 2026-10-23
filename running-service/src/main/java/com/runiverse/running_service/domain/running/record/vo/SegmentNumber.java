package com.runiverse.running_service.domain.running.record.vo;

import com.runiverse.running_service.domain.running.record.exception.InvalidSegmentNumberException;

public record SegmentNumber(int value) {

    private static final int MIN = 1;

    public SegmentNumber {
        if (value < MIN) {
            throw new InvalidSegmentNumberException();
        }
    }

    // 1번 조각은 출발점에서 시작한다 — 2번 이후는 끊긴 곳 뒤에서 시작한다
    public boolean isFirst() {
        return value == MIN;
    }
}
