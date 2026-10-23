package com.runiverse.running_service.application.user.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.vo.BackgroundImageKey;

public interface UpdateBackgroundImagePort {

    void updateBackgroundImage(UserId userId, BackgroundImageKey backgroundImageKey);
}
