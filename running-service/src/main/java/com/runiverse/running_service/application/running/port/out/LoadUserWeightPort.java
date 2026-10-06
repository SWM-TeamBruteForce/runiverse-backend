package com.runiverse.running_service.application.running.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

import java.math.BigDecimal;
import java.util.Optional;

public interface LoadUserWeightPort {

    // user_onboardings.weight — 온보딩 행이 없을 수 있어 Optional이다
    Optional<BigDecimal> loadWeightKg(UserId userId);
}
