package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.WeightOutOfRangeException;
import com.runiverse.running_service.domain.user.exception.WeightRequiredException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Weight(BigDecimal value) {

    private static final BigDecimal MIN = new BigDecimal("20.0");
    private static final BigDecimal MAX = new BigDecimal("300.0");

    public Weight {
        if (value == null) {
            throw new WeightRequiredException();
        }
        value = value.setScale(1, RoundingMode.HALF_UP);
        if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
            throw new WeightOutOfRangeException();
        }
    }
}
