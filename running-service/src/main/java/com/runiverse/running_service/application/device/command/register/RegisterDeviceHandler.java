package com.runiverse.running_service.application.device.command.register;

import com.runiverse.running_service.application.common.port.out.ExistsUserPort;
import com.runiverse.running_service.application.common.port.out.LockUserPort;
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

    private final LockUserPort lockUserPort;
    private final ExistsUserPort existsUserPort;
    private final UpsertUserDevicePort upsertUserDevicePort;
    private final DeactivateUserDevicesPort deactivateUserDevicesPort;

    @Override
    public void handle(RegisterDeviceCommand command) {
        UserDevice userDevice = UserDevice.register(
                command.userId(), command.deviceId(), command.pushToken(),
                Platform.from(command.platform()), command.appVersion());

        // 1. 같은 유저의 등록·탈퇴를 줄 세운다 — 두 기기가 동시에 등록해도 하나만 켜진다
        lockUserPort.lock(userDevice.getUserId());
        // 2. 기다리는 사이 탈퇴가 끝났으면 넣지 않는다 — 넣으면 FK 오류(500)나 주인 없는 행이 된다.
        //    앱은 이미 로그아웃된 상태라 응답을 쓸 데가 없어 204로 끝낸다
        if (!existsUserPort.existsById(userDevice.getUserId())) {
            log.info("[기기] 등록 건너뜀: 탈퇴한 유저");
            return;
        }
        // 3. 이 기기를 켠다 — 다른 계정이 쓰던 기기면 주인이 요청한 유저로 바뀐다
        upsertUserDevicePort.upsert(userDevice);
        // 4. 같은 유저의 다른 기기를 끈다
        deactivateUserDevicesPort.deactivateOthers(userDevice.getUserId(), userDevice.getDeviceId());

        log.info("[기기] 등록 성공 - platform={}, appVersion={}",
                userDevice.getPlatform(), userDevice.getAppVersion().value());
    }
}
