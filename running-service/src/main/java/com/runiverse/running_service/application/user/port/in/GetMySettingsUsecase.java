package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.query.settings.GetMySettingsQuery;
import com.runiverse.running_service.application.user.query.settings.GetMySettingsResult;

public interface GetMySettingsUsecase {

    GetMySettingsResult handle(GetMySettingsQuery query);
}
