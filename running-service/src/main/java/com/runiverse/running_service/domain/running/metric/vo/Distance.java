package com.runiverse.running_service.domain.running.metric.vo;

import com.runiverse.running_service.domain.running.metric.exception.DistanceOutOfRangeException;

public record Distance(int meters) {

    private static final int MIN = 1;
    private static final int MAX = 500_000; // 500km까지 제한인데 나중에 풀수도 있다.

    public Distance {
        if (meters < MIN || meters > MAX) {
            throw new DistanceOutOfRangeException();
        }
    }

    // 솔로 — 목표가 없다. NOT NULL이라 값은 넣어야 해서 도달 불가능한 상한으로 "끝은 유저가 정한다"를 표현한다
    public static Distance unlimited() {
        return new Distance(MAX);
    }

    public boolean isUnlimited() {   // 읽는 쪽이 500000을 목표 거리로 오해하지 않게
        return meters == MAX;
    }
}
