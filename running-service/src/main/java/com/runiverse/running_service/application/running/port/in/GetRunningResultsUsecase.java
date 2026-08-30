package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.query.result.GetRunningResultsQuery;
import com.runiverse.running_service.application.running.query.result.GetRunningResultsResult;

public interface GetRunningResultsUsecase {

    GetRunningResultsResult handle(GetRunningResultsQuery query);
}
