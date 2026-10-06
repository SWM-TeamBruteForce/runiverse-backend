package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class CaloriesOutOfRangeException extends BusinessException {

    public CaloriesOutOfRangeException() {
        super(RunningMetricErrorCode.CALORIES_OUT_OF_RANGE);
    }
}
