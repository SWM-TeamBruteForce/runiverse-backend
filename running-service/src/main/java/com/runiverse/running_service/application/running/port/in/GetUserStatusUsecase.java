package com.runiverse.running_service.application.running.port.in;

import com.runiverse.running_service.application.running.query.status.GetUserStatusQuery;
import com.runiverse.running_service.application.running.query.status.GetUserStatusResult;

public interface GetUserStatusUsecase {

    GetUserStatusResult handle(GetUserStatusQuery query);
}
