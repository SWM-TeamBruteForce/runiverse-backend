package com.runiverse.running_service.domain.running.metric.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningMetricErrorCode;

public class CadenceOutOfRangeException extends BusinessException {

    public CadenceOutOfRangeException() {
        super(RunningMetricErrorCode.CADENCE_OUT_OF_RANGE);
    }
}
