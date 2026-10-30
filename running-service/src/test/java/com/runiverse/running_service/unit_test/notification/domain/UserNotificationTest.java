package com.runiverse.running_service.unit_test.notification.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.UserNotification;
import com.runiverse.running_service.domain.notification.exception.SelfNotificationException;
import com.runiverse.running_service.domain.notification.exception.UserNotificationPayloadRequiredException;
import com.runiverse.running_service.domain.notification.exception.UserNotificationRecipientRequiredException;
import com.runiverse.running_service.domain.notification.vo.FriendRequestAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestReceivedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("알림 애그리거트 단위 테스트")
class UserNotificationTest {

    private static final UserId RECIPIENT_ID = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId ACTOR_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Test
    @DisplayName("받는 사람과 payload로 만들고 종류는 payload를 따른다")
    void createsWithTypeFromPayload() {
        // when
        UserNotification notification = UserNotification.of(RECIPIENT_ID, new FriendRequestAcceptedPayload(ACTOR_ID));

        // then
        assertThat(notification.getUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(notification.type()).isEqualTo(UserNotificationType.FRIEND_REQUEST_ACCEPTED);
        assertThat(notification.getPayload().actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("받는 사람이 없으면 만들 수 없다")
    void rejectsWithoutRecipient() {
        // when & then
        assertThatThrownBy(() -> UserNotification.of(null, new FriendRequestReceivedPayload(ACTOR_ID)))
                .isInstanceOf(UserNotificationRecipientRequiredException.class);
    }

    @Test
    @DisplayName("payload가 없으면 만들 수 없다")
    void rejectsWithoutPayload() {
        // when & then
        assertThatThrownBy(() -> UserNotification.of(RECIPIENT_ID, null))
                .isInstanceOf(UserNotificationPayloadRequiredException.class);
    }

    @Test
    @DisplayName("자기 자신이 일으킨 알림은 만들 수 없다")
    void rejectsSelfNotification() {
        // when & then -> 받는 사람과 일으킨 사람이 같으면 호출한 코드의 잘못이다
        assertThatThrownBy(() -> UserNotification.of(RECIPIENT_ID, new FriendRequestReceivedPayload(RECIPIENT_ID)))
                .isInstanceOf(SelfNotificationException.class);
    }
}
