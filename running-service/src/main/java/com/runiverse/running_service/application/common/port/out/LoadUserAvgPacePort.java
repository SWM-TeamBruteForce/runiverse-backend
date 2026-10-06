package com.runiverse.running_service.application.common.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;

import java.util.Optional;

public interface LoadUserAvgPacePort {

    Optional<Pace> loadAvgPace(UserId userId);
}
