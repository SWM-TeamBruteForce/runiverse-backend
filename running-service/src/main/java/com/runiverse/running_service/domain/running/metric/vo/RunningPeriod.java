package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.InvalidRunningPeriodException;
import com.runiverse.running_service.domain.running.metric.exception.RunningPeriodRequiredException;

import java.time.LocalDateTime;

public record RunningPeriod(LocalDateTime startAt, LocalDateTime endAt) {

    public RunningPeriod {
        if (startAt == null || endAt == null) {
            throw new RunningPeriodRequiredException();
        }
        if (!endAt.isAfter(startAt)) {
            throw new InvalidRunningPeriodException();
        }
    }

    // 이 구간이 다른 구간을 품는가 — 경계가 같아도 안에 있는 것으로 본다
    public boolean contains(RunningPeriod other) {
        return !other.startAt().isBefore(startAt) && !other.endAt().isAfter(endAt);
    }
}
