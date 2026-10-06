package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.user.vo.Introduction;
import com.runiverse.running_service.domain.common.vo.UserId;

public interface UpdateIntroductionPort {

    void updateIntroduction(UserId userId, Introduction introduction);
}
