package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.query.account.GetMyAccountQuery;
import com.runiverse.running_service.application.user.query.account.GetMyAccountResult;

public interface GetMyAccountUsecase {

    GetMyAccountResult handle(GetMyAccountQuery query);
}
