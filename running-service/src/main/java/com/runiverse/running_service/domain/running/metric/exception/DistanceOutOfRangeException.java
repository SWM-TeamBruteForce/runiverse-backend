package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class DistanceOutOfRangeException extends BusinessException {

    public DistanceOutOfRangeException() {
        super(RunningMetricErrorCode.DISTANCE_OUT_OF_RANGE);
    }
}
