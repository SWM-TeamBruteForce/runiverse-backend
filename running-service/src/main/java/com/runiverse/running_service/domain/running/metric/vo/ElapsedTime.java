package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.ElapsedTimeOutOfRangeException;

public record ElapsedTime(int seconds) {

    private static final int MIN = 1;
    private static final int MAX = 86_400; // 최대 24 시간

    public ElapsedTime {
        if (seconds < MIN || seconds > MAX) {
            throw new ElapsedTimeOutOfRangeException();
        }
    }

    // 시계가 튄 트랙인지 조립 전에 묻는다 — int로 자르기 전에 long으로 물어야 오버플로가 안 숨는다
    public static boolean isValid(long seconds) {
        return seconds >= MIN && seconds <= MAX;
    }

    public ElapsedTime plus(ElapsedTime other) {
        return new ElapsedTime(seconds + other.seconds);
    }
}
