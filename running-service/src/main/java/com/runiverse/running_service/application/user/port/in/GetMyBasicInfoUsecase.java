package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoQuery;
import com.runiverse.running_service.application.user.query.basicinfo.GetMyBasicInfoResult;

public interface GetMyBasicInfoUsecase {

    GetMyBasicInfoResult handle(GetMyBasicInfoQuery query);
}
