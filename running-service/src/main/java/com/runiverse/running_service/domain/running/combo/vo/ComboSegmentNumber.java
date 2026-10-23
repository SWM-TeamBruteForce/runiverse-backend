package com.runiverse.running_service.domain.running.combo.vo;

import com.runiverse.running_service.domain.running.combo.exception.InvalidComboSegmentNumberException;

// 이 방에서 이 쌍의 몇 번째 콤보인가 — 시작 시각 순으로 1부터 매긴다
public record ComboSegmentNumber(int value) {

    private static final int MIN = 1;

    public ComboSegmentNumber {
        if (value < MIN) {
            throw new InvalidComboSegmentNumberException();
        }
    }
}
