package com.runiverse.running_service.domain.running.combo.vo;

import com.runiverse.running_service.domain.running.combo.exception.InvalidComboCountException;

// 한 구간의 유지 횟수. 0인 구간은 붙자마자 끊겨 함께 달린 구간이 없으므로 남기지 않는다
public record ComboCount(int value) {

    private static final int MIN = 1;

    public ComboCount {
        if (value < MIN) {
            throw new InvalidComboCountException();
        }
    }
}
