package com.runiverse.running_service.application.app.port.in;

import com.runiverse.running_service.application.app.query.version.CheckAppVersionQuery;
import com.runiverse.running_service.application.app.query.version.CheckAppVersionResult;

public interface CheckAppVersionUsecase {

    CheckAppVersionResult handle(CheckAppVersionQuery query);
}
