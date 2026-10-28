package com.runiverse.running_service.application.notification.common;

import com.runiverse.running_service.application.notification.command.send.UserNotificationCreatedEvent;
import com.runiverse.running_service.application.notification.port.out.PublishUserNotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserNotificationDispatcher {

    private final PublishUserNotificationPort publishUserNotificationPort;

    // 커밋 후에 내보낸다 — 커밋 전에 쏘면 롤백된 요청의 알림을 밀어 보내거나,
    // 받는 쪽 인스턴스가 아직 보이지 않는 알림을 읽으러 간다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(UserNotificationCreatedEvent event) {
        publishUserNotificationPort.publish(event.userId(), event.notificationId());
    }
}
