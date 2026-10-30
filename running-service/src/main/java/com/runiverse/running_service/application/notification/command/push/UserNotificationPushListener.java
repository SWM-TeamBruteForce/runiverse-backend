package com.runiverse.running_service.application.notification.command.push;

import com.runiverse.running_service.application.notification.command.send.UserNotificationCreatedEvent;
import com.runiverse.running_service.application.notification.port.in.SendUserNotificationPushUsecase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserNotificationPushListener {

    private final SendUserNotificationPushUsecase sendUserNotificationPushUsecase;

    // 커밋 뒤에 읽어야 알림이 보이고, 그때의 상태로 보낼지 정한다
    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(UserNotificationCreatedEvent event) {
        sendUserNotificationPushUsecase.handle(
                new SendUserNotificationPushCommand(event.userId().value(), event.notificationId()));
    }
}
