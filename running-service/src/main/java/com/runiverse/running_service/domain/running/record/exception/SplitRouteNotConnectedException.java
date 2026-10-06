package com.runiverse.running_service.domain.running.record.exception;

import com.runiverse.running_service.domain.common.exception.BusinessException;
import com.runiverse.running_service.domain.common.exception.RunningRecordErrorCode;

public class SplitRouteNotConnectedException extends BusinessException {

    public SplitRouteNotConnectedException() {
        super(RunningRecordErrorCode.SPLIT_ROUTE_NOT_CONNECTED);
    }
}
