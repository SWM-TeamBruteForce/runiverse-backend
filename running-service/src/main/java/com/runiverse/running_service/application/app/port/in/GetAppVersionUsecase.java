package com.runiverse.running_service.application.app.port.in;

import com.runiverse.running_service.application.app.query.version.GetAppVersionQuery;
import com.runiverse.running_service.application.app.query.version.GetAppVersionResult;

public interface GetAppVersionUsecase {

    GetAppVersionResult handle(GetAppVersionQuery query);
}
