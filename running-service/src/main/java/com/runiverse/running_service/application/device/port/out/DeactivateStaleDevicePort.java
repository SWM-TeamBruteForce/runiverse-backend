package com.runiverse.running_service.application.device.port.out;

import com.runiverse.running_service.domain.device.vo.DeviceId;
import com.runiverse.running_service.domain.device.vo.PushToken;

public interface DeactivateStaleDevicePort {

    // 토큰이 그대로일 때만 끈다 — 보내는 사이 앱이 새 토큰을 등록했으면 그 기기는 살아 있다
    void deactivateStale(DeviceId deviceId, PushToken pushToken);
}
