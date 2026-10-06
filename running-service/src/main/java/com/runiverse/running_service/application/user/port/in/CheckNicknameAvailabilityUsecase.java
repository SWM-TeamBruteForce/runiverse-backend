package com.runiverse.running_service.application.user.port.in;

import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityQuery;
import com.runiverse.running_service.application.user.query.nickname.CheckNicknameAvailabilityResult;

public interface CheckNicknameAvailabilityUsecase {

    CheckNicknameAvailabilityResult handle(CheckNicknameAvailabilityQuery query);
}
