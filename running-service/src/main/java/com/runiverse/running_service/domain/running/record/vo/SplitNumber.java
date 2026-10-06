package com.runiverse.running_service.domain.running.record.vo;

import com.runiverse.running_service.domain.running.record.exception.InvalidSplitNumberException;

public record SplitNumber(int value) {

    private static final int MIN = 1;

    public SplitNumber {
        if (value < MIN) {
            throw new InvalidSplitNumberException();
        }
    }

    public SplitNumber next() {
        return new SplitNumber(value + 1);
    }

    // min 값이 바뀔수 있음으로
    public boolean isFirst() {
        return value == MIN;
    }
}
