package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.TemperatureOutOfRangeException;
import com.runiverse.running_service.domain.running.metric.exception.TemperatureRequiredException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Temperature(BigDecimal celsius) {

    private static final BigDecimal MIN = new BigDecimal("-99.9");
    private static final BigDecimal MAX = new BigDecimal("99.9");

    public Temperature {
        if (celsius == null) {
            throw new TemperatureRequiredException();
        }
        celsius = celsius.setScale(1, RoundingMode.HALF_UP);
        if (celsius.compareTo(MIN) < 0 || celsius.compareTo(MAX) > 0) {
            throw new TemperatureOutOfRangeException();
        }
    }
}
