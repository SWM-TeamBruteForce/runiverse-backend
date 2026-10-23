package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SplitRouteNotStartingAtSegmentException extends BusinessException {

    public SplitRouteNotStartingAtSegmentException() {
        super(RunningRecordErrorCode.SPLIT_ROUTE_NOT_STARTING_AT_SEGMENT);
    }
}
