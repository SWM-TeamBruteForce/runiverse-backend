package com.runiverse.running_service.unit_test.notification.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.exception.UserNotificationActorRequiredException;
import com.runiverse.running_service.domain.notification.exception.UserNotificationRunningRoomRequiredException;
import com.runiverse.running_service.domain.notification.vo.FriendRequestAcceptedPayload;
import com.runiverse.running_service.domain.notification.vo.FriendRequestReceivedPayload;
import com.runiverse.running_service.domain.notification.vo.RunningInvitationReceivedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("알림 payload 단위 테스트")
class UserNotificationPayloadTest {

    private static final UserId ACTOR_ID = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Test
    @DisplayName("친구 요청 payload는 요청한 사람을 일으킨 사람으로 갖고 FRIEND_REQUEST_RECEIVED다")
    void friendRequestReceivedCarriesRequester() {
        // when
        FriendRequestReceivedPayload payload = new FriendRequestReceivedPayload(ACTOR_ID);

        // then -> type은 구현체가 정해 어긋날 수 없다
        assertThat(payload.type()).isEqualTo(UserNotificationType.FRIEND_REQUEST_RECEIVED);
        assertThat(payload.actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("친구 수락 payload는 수락한 사람을 일으킨 사람으로 갖고 FRIEND_REQUEST_ACCEPTED다")
    void friendRequestAcceptedCarriesAccepter() {
        // when
        FriendRequestAcceptedPayload payload = new FriendRequestAcceptedPayload(ACTOR_ID);

        // then
        assertThat(payload.type()).isEqualTo(UserNotificationType.FRIEND_REQUEST_ACCEPTED);
        assertThat(payload.actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("초대 payload는 초대한 사람을 일으킨 사람으로 갖고 방과 함께 RUNNING_INVITATION_RECEIVED다")
    void runningInvitationReceivedCarriesInviterAndRoom() {
        // when
        RunningInvitationReceivedPayload payload =
                new RunningInvitationReceivedPayload(ACTOR_ID, new RunningRoomId(130L));

        // then
        assertThat(payload.type()).isEqualTo(UserNotificationType.RUNNING_INVITATION_RECEIVED);
        assertThat(payload.actorId()).isEqualTo(ACTOR_ID);
        assertThat(payload.runningRoomId()).isEqualTo(new RunningRoomId(130L));
    }

    @Test
    @DisplayName("초대한 사람이 없으면 초대 payload를 만들 수 없다")
    void rejectsRunningInvitationReceivedWithoutInviter() {
        // when & then
        assertThatThrownBy(() -> new RunningInvitationReceivedPayload(null, new RunningRoomId(130L)))
                .isInstanceOf(UserNotificationActorRequiredException.class);
    }

    @Test
    @DisplayName("방이 없으면 초대 payload를 만들 수 없다")
    void rejectsRunningInvitationReceivedWithoutRoom() {
        // when & then -> 방이 없으면 그 초대의 지금 상태를 찾을 수 없다
        assertThatThrownBy(() -> new RunningInvitationReceivedPayload(ACTOR_ID, null))
                .isInstanceOf(UserNotificationRunningRoomRequiredException.class);
    }

    @Test
    @DisplayName("요청한 사람이 없으면 친구 요청 payload를 만들 수 없다")
    void rejectsFriendRequestReceivedWithoutRequester() {
        // when & then -> DB의 깨진 JSON을 되살릴 때도 이 생성자를 거쳐 걸러진다
        assertThatThrownBy(() -> new FriendRequestReceivedPayload(null))
                .isInstanceOf(UserNotificationActorRequiredException.class);
    }

    @Test
    @DisplayName("수락한 사람이 없으면 친구 수락 payload를 만들 수 없다")
    void rejectsFriendRequestAcceptedWithoutAccepter() {
        // when & then
        assertThatThrownBy(() -> new FriendRequestAcceptedPayload(null))
                .isInstanceOf(UserNotificationActorRequiredException.class);
    }
}
