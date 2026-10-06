package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface ExistsOnboardingPort {

    boolean existsByUserId(UserId userId);
}
