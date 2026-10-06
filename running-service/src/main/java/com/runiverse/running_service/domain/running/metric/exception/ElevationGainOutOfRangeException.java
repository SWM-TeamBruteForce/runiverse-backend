package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class ElevationGainOutOfRangeException extends BusinessException {

    public ElevationGainOutOfRangeException() {
        super(RunningMetricErrorCode.ELEVATION_GAIN_OUT_OF_RANGE);
    }
}
