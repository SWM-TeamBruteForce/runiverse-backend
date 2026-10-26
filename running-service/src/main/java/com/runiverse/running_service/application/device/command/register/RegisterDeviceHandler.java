package com.runiverse.running_service.application.device.command.register;

import com.runiverse.running_service.application.device.port.in.RegisterDeviceUsecase;
import com.runiverse.running_service.application.device.port.out.DeactivateUserDevicesPort;
import com.runiverse.running_service.application.device.port.out.UpsertUserDevicePort;
import com.runiverse.running_service.domain.common.vo.Platform;
import com.runiverse.running_service.domain.device.UserDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인 세션이 계정당 하나라 푸시도 유저당 마지막에 등록한 기기 하나로만 보낸다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegisterDeviceHandler implements RegisterDeviceUsecase {

    private final UpsertUserDevicePort upsertUserDevicePort;
    private final DeactivateUserDevicesPort deactivateUserDevicesPort;

    @Override
    public void handle(RegisterDeviceCommand command) {
        UserDevice userDevice = UserDevice.register(
                command.userId(), command.deviceId(), command.pushToken(),
                Platform.from(command.platform()), command.appVersion());

        // 1. 이 기기를 켠다 — 다른 계정이 쓰던 기기면 주인이 요청한 유저로 바뀐다
        upsertUserDevicePort.upsert(userDevice);
        // 2. 같은 유저의 다른 기기를 끈다. users 행을 잠그지 않아 한 유저의 두 기기가 동시에 등록하면 둘 다 켜질 수 있다 —
        //    잠그면 탈퇴와 잠금 순서가 엇갈려 교착에 빠진다
        deactivateUserDevicesPort.deactivateOthers(userDevice.getUserId(), userDevice.getDeviceId());

        log.info("[기기] 등록 성공 - platform={}, appVersion={}",
                userDevice.getPlatform(), userDevice.getAppVersion().value());
    }
}
