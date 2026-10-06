package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class SplitPaceOutOfRangeException extends BusinessException {

    public SplitPaceOutOfRangeException() {
        super(RunningMetricErrorCode.SPLIT_PACE_OUT_OF_RANGE);
    }
}
