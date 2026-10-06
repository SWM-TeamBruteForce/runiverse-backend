package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.query.profile.GetMyProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetMyProfileResult;

public interface GetMyProfileUsecase {

    GetMyProfileResult handle(GetMyProfileQuery query);
}
