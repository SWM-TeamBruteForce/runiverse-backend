package com.runiverse.running_service.application.device.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.device.vo.DeviceId;

public interface DeactivateUserDevicesPort {

    // 방금 등록한 기기만 남기고 그 유저의 다른 기기를 끈다
    void deactivateOthers(UserId userId, DeviceId deviceId);

    void deactivateAll(UserId userId);
}
