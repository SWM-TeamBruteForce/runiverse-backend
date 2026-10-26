package com.runiverse.running_service.application.device.port.out;

import com.runiverse.running_service.domain.device.UserDevice;

public interface UpsertUserDevicePort {

    // 같은 기기 ID의 행이 있으면 주인·토큰·플랫폼·앱 버전을 덮어쓰고 켠다
    void upsert(UserDevice userDevice);
}
