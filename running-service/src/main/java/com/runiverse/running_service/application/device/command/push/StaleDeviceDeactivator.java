package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.device.port.out.DeactivateStaleDevicePort;
import com.runiverse.running_service.application.device.port.out.PushTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 다시 보내도 닿지 않는 토큰의 기기를 끈다 — FCM 응답을 받은 뒤 이 갱신만 짧은 트랜잭션으로 연다
@Component
@RequiredArgsConstructor
public class StaleDeviceDeactivator {

    private final DeactivateStaleDevicePort deactivateStaleDevicePort;

    // 토큰을 함께 넘겨야 그사이 새 토큰을 등록한 기기를 끄지 않는다
    @Transactional
    public void deactivate(PushTarget target) {
        deactivateStaleDevicePort.deactivateStale(target.deviceId(), target.pushToken());
    }
}
