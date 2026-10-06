package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.CaloriesOutOfRangeException;

public record Calories(int kcal) {

    private static final int MIN = 0;
    private static final int MAX = 20_000;

    public Calories {
        if (kcal < MIN || kcal > MAX) {
            throw new CaloriesOutOfRangeException();
        }
    }
}
