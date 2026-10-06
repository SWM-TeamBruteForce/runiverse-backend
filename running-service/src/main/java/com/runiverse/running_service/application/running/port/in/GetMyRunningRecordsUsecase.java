package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsQuery;
import com.runiverse.running_service.application.running.query.record.GetMyRunningRecordsResult;

public interface GetMyRunningRecordsUsecase {

    GetMyRunningRecordsResult handle(GetMyRunningRecordsQuery query);
}
