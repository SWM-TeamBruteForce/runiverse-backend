package com.runiverse.running_service.application.device.command.push;

import com.runiverse.running_service.application.common.port.out.LoadUserByIdPort;
import com.runiverse.running_service.application.device.port.out.LoadPushTargetPort;
import com.runiverse.running_service.application.device.port.out.PushTarget;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// 보낼 수 있는 기기를 고른다 — DB 읽기만 짧은 트랜잭션으로 묶어, FCM을 기다리는 동안 커넥션을 쥐지 않게 한다
@Slf4j
@Component
@RequiredArgsConstructor
public class PushTargetReader {

    private final LoadPushTargetPort loadPushTargetPort;
    private final LoadUserByIdPort loadUserByIdPort;

    // 켜진 기기가 없거나, 탈퇴했거나, 알림을 껐으면 비어 있다. 건너뛴 이유는 여기서 남긴다
    @Transactional(readOnly = true)
    public Optional<PushTarget> read(UserId userId, PushType pushType) {
        // 1. 켜진 기기가 없으면 보낼 곳이 없다 — 로그아웃했거나 등록한 적이 없다
        Optional<PushTarget> target = loadPushTargetPort.loadPushTarget(userId);
        if (target.isEmpty()) {
            log.info("[기기] 푸시 발송 건너뜀: 켜진 기기 없음 - targetUserId={}, pushType={}",
                    userId.value(), pushType);
            return Optional.empty();
        }
        // 2. 알림을 끈 유저에게는 보내지 않는다
        Optional<User> user = loadUserByIdPort.loadById(userId);
        if (user.isEmpty()) {
            log.info("[기기] 푸시 발송 건너뜀: 탈퇴한 유저 - targetUserId={}, pushType={}", userId.value(), pushType);
            return Optional.empty();
        }
        if (!user.get().isAlertConsent()) {
            log.info("[기기] 푸시 발송 건너뜀: 알림 꺼짐 - targetUserId={}, pushType={}", userId.value(), pushType);
            return Optional.empty();
        }
        return target;
    }
}
