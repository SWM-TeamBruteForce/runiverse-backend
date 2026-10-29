package com.runiverse.running_service.application.device.port.out;

import com.runiverse.running_service.domain.device.vo.DeviceId;
import com.runiverse.running_service.domain.device.vo.PushToken;

public record PushTarget(DeviceId deviceId, PushToken pushToken) {

}
