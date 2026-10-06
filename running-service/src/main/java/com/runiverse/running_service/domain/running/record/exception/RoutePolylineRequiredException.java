package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class RoutePolylineRequiredException extends BusinessException {

    public RoutePolylineRequiredException() {
        super(RunningRecordErrorCode.ROUTE_POLYLINE_REQUIRED);
    }
}
