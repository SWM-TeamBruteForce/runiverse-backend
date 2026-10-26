package com.runiverse.running_service.application.device.port.out;

import com.runiverse.running_service.domain.common.vo.UserId;

public interface DeleteUserDevicesPort {

    void deleteAll(UserId userId);
}
