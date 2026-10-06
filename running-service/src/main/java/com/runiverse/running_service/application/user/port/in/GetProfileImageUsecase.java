package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlQuery;
import com.runiverse.running_service.application.user.query.profileimage.GetProfileImageUrlResult;

public interface GetProfileImageUsecase {

    GetProfileImageUrlResult handle(GetProfileImageUrlQuery query);
}
