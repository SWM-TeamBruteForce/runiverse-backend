package com.runiverse.running_service.domain.user.vo;

import com.runiverse.running_service.domain.user.exception.BmiOutOfRangeException;
import com.runiverse.running_service.domain.user.exception.BmiRequiredException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Bmi(BigDecimal value) {

    private static final int SCALE = 1;
    private static final BigDecimal SQUARE_CM_PER_SQUARE_METER = new BigDecimal("10000");

    public Bmi {
        if (value == null) {
            throw new BmiRequiredException();
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            throw new BmiOutOfRangeException();
        }
    }

    // 체중(kg) ÷ 신장(m)² — 반올림은 마지막 나눗셈에서 한 번만 한다
    public static Bmi from(Weight weight, Height height) {
        return new Bmi(weight.value()
                .multiply(SQUARE_CM_PER_SQUARE_METER)
                .divide(height.value().multiply(height.value()), SCALE, RoundingMode.HALF_UP));
    }
}
