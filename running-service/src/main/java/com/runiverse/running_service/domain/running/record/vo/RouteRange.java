package com.runiverse.running_service.domain.running.record.vo;

import com.runiverse.running_service.domain.running.record.exception.InvalidRouteRangeException;

public record RouteRange(int startIndex, int endIndex) {

    public RouteRange {
        if (startIndex < 0 || endIndex < startIndex) {
            throw new InvalidRouteRangeException();
        }
    }

    // 다음 구간은 이 구간의 끝점에서 시작
    public boolean connectsTo(RouteRange next) {
        return endIndex == next.startIndex;
    }
    
}
