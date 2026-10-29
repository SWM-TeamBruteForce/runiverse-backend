package com.runiverse.running_service.application.notification.command.broadcast;

import com.runiverse.running_service.application.app.port.out.AppStreamConnection;
import com.runiverse.running_service.application.app.port.out.AppStreamEvent;
import com.runiverse.running_service.application.app.port.out.AppStreamPort;
import com.runiverse.running_service.application.notification.common.UserNotificationItemReader;
import com.runiverse.running_service.application.notification.port.in.BroadcastUserNotificationUsecase;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

// 모든 인스턴스가 메시지마다 부른다 — 받는 사람이 이 인스턴스에 붙어 있을 때만 일한다.
// 트랜잭션을 걸지 않는다: 붙어 있지 않은 인스턴스가 DB 커넥션을 잡지 않게 읽기는 리더가 연다
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastUserNotificationHandler implements BroadcastUserNotificationUsecase {

    private final AppStreamPort appStreamPort;
    private final UserNotificationItemReader userNotificationItemReader;

    @Override
    public void handle(BroadcastUserNotificationCommand command) {
        UserId userId = new UserId(command.userId());

        // 1. 이 인스턴스에 붙어 있지 않으면 끝 — 붙은 인스턴스가 따로 보낸다. 로그도 남기지 않는다
        Optional<AppStreamConnection> connection = appStreamPort.find(userId);
        if (connection.isEmpty()) {
            return;
        }

        // 2. 커밋 뒤라 지금 상태로 조립된다 — 알림 목록 항목과 같은 모양이다
        Optional<GetUserNotificationsResult.Item> item =
                userNotificationItemReader.read(userId, command.notificationId());
        if (item.isEmpty()) {
            // 깨진 행·초대 행 없음은 어댑터·조립기가 원인을 남겼다. 여기서는 못 보낸 사실만 남긴다
            log.warn("[알림] 푸시 건너뜀: 알림 없음 - notificationId={}", command.notificationId());
            return;
        }

        // 3. 끊긴 단말이면 연결이 스스로 닫고 레지스트리에서 빠진다 — 알림은 다음 목록 조회로 보인다
        connection.get().send(AppStreamEvent.notification(item.get()));
        log.info("[알림] 푸시 성공 - notificationId={}, connectionId={}",
                command.notificationId(), connection.get().id());
    }
}
