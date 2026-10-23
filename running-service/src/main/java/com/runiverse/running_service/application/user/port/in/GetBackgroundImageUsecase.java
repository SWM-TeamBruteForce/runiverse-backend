package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlQuery;
import com.runiverse.running_service.application.user.query.backgroundimage.GetBackgroundImageUrlResult;

public interface GetBackgroundImageUsecase {

    GetBackgroundImageUrlResult handle(GetBackgroundImageUrlQuery query);
}
