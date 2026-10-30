package com.runiverse.running_service.unit_test.notification.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.notification.command.send.SendUserNotificationHandler;
import com.runiverse.running_service.application.notification.command.send.UserNotificationCreatedEvent;
import com.runiverse.running_service.application.notification.port.out.SaveUserNotificationPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.UserNotification;
import com.runiverse.running_service.domain.notification.exception.SelfNotificationException;
import com.runiverse.running_service.domain.notification.vo.FriendRequestReceivedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 보내기 단위 테스트")
class SendUserNotificationHandlerTest {

    private static final UserId RECIPIENT_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId ACTOR_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private SaveUserNotificationPort saveUserNotificationPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SendUserNotificationHandler handler;

    @Test
    @DisplayName("받는 사람과 payload로 알림을 만들어 저장한다")
    void savesNotification() {
        // given
        given(saveUserNotificationPort.save(any())).willReturn(912L);

        // when
        handler.send(RECIPIENT_ID, new FriendRequestReceivedPayload(ACTOR_ID));

        // then
        ArgumentCaptor<UserNotification> saved = ArgumentCaptor.forClass(UserNotification.class);
        verify(saveUserNotificationPort).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(saved.getValue().type()).isEqualTo(UserNotificationType.FRIEND_REQUEST_RECEIVED);
        assertThat(saved.getValue().getPayload().actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("저장한 뒤 받는 사람과 생성된 ID를 담은 이벤트를 발행한다")
    void publishesCreatedEventAfterSave() {
        // given
        given(saveUserNotificationPort.save(any())).willReturn(912L);

        // when
        handler.send(RECIPIENT_ID, new FriendRequestReceivedPayload(ACTOR_ID));

        // then -> 밖으로 내보내는 일은 이 이벤트를 받는 리스너가 커밋 뒤에 한다
        InOrder order = inOrder(saveUserNotificationPort, eventPublisher);
        order.verify(saveUserNotificationPort).save(any());
        order.verify(eventPublisher).publishEvent(new UserNotificationCreatedEvent(RECIPIENT_ID, 912L));
    }

    @Test
    @DisplayName("도메인 검증에 걸리면 저장도 발행도 하지 않고 예외를 그대로 던진다")
    void rejectsInvalidNotificationWithoutSaving() {
        // when & then -> 부르는 쪽 트랜잭션이 함께 롤백되도록 삼키지 않는다
        assertThatThrownBy(() -> handler.send(RECIPIENT_ID, new FriendRequestReceivedPayload(RECIPIENT_ID)))
                .isInstanceOf(SelfNotificationException.class);
        verifyNoInteractions(saveUserNotificationPort, eventPublisher);
    }
}
