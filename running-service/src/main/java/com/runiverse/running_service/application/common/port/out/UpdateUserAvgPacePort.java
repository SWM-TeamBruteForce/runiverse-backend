package com.runiverse.running_service.application.common.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.AvgPace;

public interface UpdateUserAvgPacePort {

    void updateAvgPace(UserId userId, AvgPace avgPace);
}
