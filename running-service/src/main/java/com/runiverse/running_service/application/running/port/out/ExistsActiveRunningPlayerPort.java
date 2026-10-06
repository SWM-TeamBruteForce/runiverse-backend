package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface ExistsActiveRunningPlayerPort {

    boolean existsActive(UserId userId);
}
