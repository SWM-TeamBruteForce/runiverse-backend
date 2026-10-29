package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.auth.port.out.LoadRefreshTokenPort;
import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import com.runiverse.running_service.application.device.port.out.PushSendStatus;
import com.runiverse.running_service.application.device.port.out.PushTarget;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.device.port.out.SendPushPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

// 받는 사람은 요청자가 아닐 수 있어 로그에 targetUserId로 남긴다.
// 트랜잭션을 걸지 않는다: FCM을 기다리는 동안 DB 커넥션을 쥐지 않게 읽기와 기기 끄기가 각자 짧게 연다
@Slf4j
@Service
@RequiredArgsConstructor
public class SendPushHandler implements SendPushUsecase {

    private final PushTargetReader pushTargetReader;
    private final LoadRefreshTokenPort loadRefreshTokenPort;
    private final SendPushPort sendPushPort;
    private final StaleDeviceDeactivator staleDeviceDeactivator;

    @Override
    public void handle(SendPushCommand command) {
        UserId userId = new UserId(command.userId());
        PushType pushType = command.message().type();

        // 1. 켜진 기기·알림 동의를 본다. 건너뛴 이유는 리더가 남긴다
        Optional<PushTarget> target = pushTargetReader.read(userId, pushType);
        if (target.isEmpty()) {
            return;
        }
        // 2. 로그인 세션이 없으면 보내지 않는다 — 로그아웃 요청이 실패했거나 세션이 만료·폐기돼도
        //    기기는 켜진 채 남아, 로그아웃된 폰에 알림이 갈 수 있다
        if (loadRefreshTokenPort.load(userId).isEmpty()) {
            log.info("[기기] 푸시 발송 건너뜀: 로그인 세션 없음 - targetUserId={}, pushType={}",
                    userId.value(), pushType);
            return;
        }
        // 3. 만료 시각이 지났으면 보내지 않는다 — 발송 대기 줄에서 기다리는 사이 지났을 수 있다
        LocalDateTime expiresAt = command.message().expiresAt();
        if (expiresAt != null && !LocalDateTime.now().isBefore(expiresAt)) {
            log.info("[기기] 푸시 발송 건너뜀: 만료 시각 지남 - targetUserId={}, pushType={}",
                    userId.value(), pushType);
            return;
        }
        // 4. 보낸다. 실패 원인은 발송 어댑터가 남긴다
        PushSendStatus status = sendPushPort.send(userId, target.get().pushToken(), command.message());
        // 5. 다시 보내도 닿지 않는 토큰이면 기기를 끈다
        switch (status) {
            case SENT -> log.info("[기기] 푸시 발송 성공 - targetUserId={}, pushType={}", userId.value(), pushType);
            case UNREGISTERED -> {
                staleDeviceDeactivator.deactivate(target.get());
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
