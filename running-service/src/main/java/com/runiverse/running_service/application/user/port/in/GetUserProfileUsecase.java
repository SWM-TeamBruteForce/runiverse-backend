package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.query.profile.GetUserProfileQuery;
import com.runiverse.running_service.application.user.query.profile.GetUserProfileResult;

public interface GetUserProfileUsecase {

    GetUserProfileResult handle(GetUserProfileQuery query);
}
