package com.runiverse.running_service.domain.running.record.vo;

import com.runiverse.running_service.domain.running.record.exception.RoutePolylineRequiredException;

public record RoutePolyline(String value) {

    public RoutePolyline {
        // text 칼럼이라서 따로 제한을 두지는 않음
        if (value == null) {
            throw new RoutePolylineRequiredException();
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new RoutePolylineRequiredException();
        }
    }
}
