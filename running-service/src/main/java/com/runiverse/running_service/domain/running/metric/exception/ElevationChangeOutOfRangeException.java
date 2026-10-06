package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class ElevationChangeOutOfRangeException extends BusinessException {

    public ElevationChangeOutOfRangeException() {
        super(RunningMetricErrorCode.ELEVATION_CHANGE_OUT_OF_RANGE);
    }
}
