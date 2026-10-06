package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsQuery;
import com.runiverse.running_service.application.running.query.split.GetRunningSplitResultsResult;

public interface GetRunningSplitResultsUsecase {

    GetRunningSplitResultsResult handle(GetRunningSplitResultsQuery query);
}
