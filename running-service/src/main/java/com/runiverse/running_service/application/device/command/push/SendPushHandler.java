package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.auth.port.out.LoadRefreshTokenPort;
import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import com.runiverse.running_service.application.device.port.out.DeactivateStaleDevicePort;
import com.runiverse.running_service.application.device.port.out.LoadPushTargetPort;
import com.runiverse.running_service.application.device.port.out.PushSendStatus;
import com.runiverse.running_service.application.device.port.out.PushTarget;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.device.port.out.SendPushPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

// 받는 사람은 요청자가 아닐 수 있어 로그에 targetUserId로 남긴다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SendPushHandler implements SendPushUsecase {

    private final LoadPushTargetPort loadPushTargetPort;
    private final LoadUserByIdPort loadUserByIdPort;
    private final LoadRefreshTokenPort loadRefreshTokenPort;
    private final SendPushPort sendPushPort;
    private final DeactivateStaleDevicePort deactivateStaleDevicePort;

    @Override
    public void handle(SendPushCommand command) {
        UserId userId = new UserId(command.userId());
        PushType pushType = command.message().type();

        // 1. 켜진 기기가 없으면 보낼 곳이 없다 — 로그아웃했거나 등록한 적이 없다
        Optional<PushTarget> target = loadPushTargetPort.loadPushTarget(userId);
        if (target.isEmpty()) {
            log.info("[기기] 푸시 발송 건너뜀: 켜진 기기 없음 - targetUserId={}, pushType={}",
                    userId.value(), pushType);
            return;
        }
        // 2. 알림을 끈 유저에게는 보내지 않는다
        Optional<User> user = loadUserByIdPort.loadById(userId);
        if (user.isEmpty()) {
            log.info("[기기] 푸시 발송 건너뜀: 탈퇴한 유저 - targetUserId={}, pushType={}", userId.value(), pushType);
            return;
        }
        if (!user.get().isAlertConsent()) {
            log.info("[기기] 푸시 발송 건너뜀: 알림 꺼짐 - targetUserId={}, pushType={}", userId.value(), pushType);
            return;
        }
        // 3. 로그인 세션이 없으면 보내지 않는다 — 로그아웃 요청이 실패했거나 세션이 만료·폐기돼도
        //    기기는 켜진 채 남아, 로그아웃된 폰에 알림이 갈 수 있다
        if (loadRefreshTokenPort.load(userId).isEmpty()) {
            log.info("[기기] 푸시 발송 건너뜀: 로그인 세션 없음 - targetUserId={}, pushType={}",
                    userId.value(), pushType);
            return;
        }
        // 4. 만료 시각이 지났으면 보내지 않는다 — 발송 대기 줄에서 기다리는 사이 지났을 수 있다
        LocalDateTime expiresAt = command.message().expiresAt();
        if (expiresAt != null && !LocalDateTime.now().isBefore(expiresAt)) {
            log.info("[기기] 푸시 발송 건너뜀: 만료 시각 지남 - targetUserId={}, pushType={}",
                    userId.value(), pushType);
            return;
        }
        // 5. 보낸다. 실패 원인은 발송 어댑터가 남긴다
        PushSendStatus status = sendPushPort.send(target.get().pushToken(), command.message());
        // 6. 다시 보내도 닿지 않는 토큰이면 기기를 끈다
        switch (status) {
            case SENT -> log.info("[기기] 푸시 발송 성공 - targetUserId={}, pushType={}", userId.value(), pushType);
            case UNREGISTERED -> {
                deactivateStaleDevicePort.deactivateStale(target.get().deviceId(), target.get().pushToken());
                log.info("[기기] 푸시 발송 실패: 등록 해제된 토큰 - targetUserId={}, pushType={}",
                        userId.value(), pushType);
            }
            case FAILED -> {
                // 원인은 발송 어댑터가 ERROR로 남겼다 — 기기는 그대로 두고 다음 푸시에 다시 시도된다
            }
            case EXPIRED -> log.info("[기기] 푸시 발송 건너뜀: 만료 시각 지남 - targetUserId={}, pushType={}",
                    userId.value(), pushType);
        }
    }
}
