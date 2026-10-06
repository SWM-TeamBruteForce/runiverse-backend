package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.HeightOutOfRangeException;
import com.runiverse.running_service.domain.user.exception.HeightRequiredException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Height(BigDecimal value) {

    private static final BigDecimal MIN = new BigDecimal("20.0");
    private static final BigDecimal MAX = new BigDecimal("300.0");

    public Height {
        if (value == null) {
            throw new HeightRequiredException();
        }
        value = value.setScale(1, RoundingMode.HALF_UP);
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new HeightOutOfRangeException();
        }
    }
}
