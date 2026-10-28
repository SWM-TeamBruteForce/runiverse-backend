package com.runiverse.running_service.unit_test.notification.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.notification.command.send.UserNotificationCreatedEvent;
import com.runiverse.running_service.application.notification.common.UserNotificationDispatcher;
import com.runiverse.running_service.application.notification.port.out.PublishUserNotificationPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 커밋 후 발행 단위 테스트")
class UserNotificationDispatcherTest {

    private static final UserId RECIPIENT_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private PublishUserNotificationPort publishUserNotificationPort;

    @InjectMocks
    private UserNotificationDispatcher dispatcher;

    @Test
    @DisplayName("받은 이벤트의 받는 사람과 알림 ID를 그대로 내보낸다")
    void publishesRecipientAndId() {
        // when
        dispatcher.dispatch(new UserNotificationCreatedEvent(RECIPIENT_ID, 912L));

        // then
        verify(publishUserNotificationPort).publish(RECIPIENT_ID, 912L);
    }

    @Test
    @DisplayName("커밋이 끝난 뒤에만 듣는다")
    void listensAfterCommit() throws NoSuchMethodException {
        // when -> 커밋 전에 내보내면 롤백된 요청의 알림이 나가고, 받는 쪽이 아직 없는 행을 읽으러 간다
        TransactionalEventListener listener = UserNotificationDispatcher.class
                .getMethod("dispatch", UserNotificationCreatedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        // then
        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
