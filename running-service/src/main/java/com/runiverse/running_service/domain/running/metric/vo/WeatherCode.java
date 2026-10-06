package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.WeatherCodeOutOfRangeException;

public record WeatherCode(int value) {

    private static final int MIN = 0;
    private static final int MAX = 99; // WMO 4677 코드표 범위

    public WeatherCode {
        if (value < MIN || value > MAX) {
            throw new WeatherCodeOutOfRangeException();
        }
    }
}
