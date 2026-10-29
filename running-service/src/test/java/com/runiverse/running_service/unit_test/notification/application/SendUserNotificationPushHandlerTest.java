package com.runiverse.running_service.unit_test.notification.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.device.command.push.SendPushCommand;
import com.runiverse.running_service.application.device.port.in.SendPushUsecase;
import com.runiverse.running_service.application.device.port.out.PushMessage;
import com.runiverse.running_service.application.device.port.out.PushType;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.notification.command.push.SendUserNotificationPushCommand;
import com.runiverse.running_service.application.notification.command.push.SendUserNotificationPushHandler;
import com.runiverse.running_service.application.notification.common.UserNotificationItemReader;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 기기 푸시 단위 테스트")
class SendUserNotificationPushHandlerTest {

    private static final UUID ME = UuidCreator.getTimeOrderedEpoch();
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final long NOTIFICATION_ID = 912L;
    private static final long ROOM_ID = 130L;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 10, 29, 21, 30);
    private static final SendUserNotificationPushCommand COMMAND =
            new SendUserNotificationPushCommand(ME, NOTIFICATION_ID);

    @Mock
    private UserNotificationItemReader userNotificationItemReader;

    @Mock
    private SendPushUsecase sendPushUsecase;

    private SendUserNotificationPushHandler handler;

    @BeforeEach
    void setUp() {
        MatchProperties matchProperties = new MatchProperties(
                Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ofHours(6),
                10, Duration.ofMinutes(20),
                LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
        InviteProperties inviteProperties = new InviteProperties(
                Duration.ofMinutes(10), CLOSE_OFFSET, 1_000, 42_195, 5);
        handler = new SendUserNotificationPushHandler(userNotificationItemReader,
                new InviteStartPolicy(inviteProperties, matchProperties), sendPushUsecase);
    }

    @Test
    @DisplayName("친구 요청 알림은 요청한 사람의 닉네임으로 만료 없이 보낸다")
    void sendsFriendRequested() {
        // given
        givenItem(item(UserNotificationType.FRIEND_REQUESTED, actor(false), null));

        // when
        handler.handle(COMMAND);

        // then -> 늦게 닿아도 의미가 있는 알림이다
        SendPushCommand command = sentCommand();
        assertThat(command.userId()).isEqualTo(ME);
        assertThat(command.message()).isEqualTo(new PushMessage(PushType.FRIEND_REQUESTED,
                "친구 요청이 왔어요", "철수님이 친구 요청을 보냈어요",
                Map.of("actorId", CHULSU.toString(), "notificationId", "912"), null));
    }

    @Test
    @DisplayName("친구 성립 알림은 수락한 사람의 닉네임으로 보낸다")
    void sendsFriendAccepted() {
        // given
        givenItem(item(UserNotificationType.FRIEND_ACCEPTED, actor(false), null));

        // when
        handler.handle(COMMAND);

        // then
        assertThat(sentCommand().message()).isEqualTo(new PushMessage(PushType.FRIEND_ACCEPTED,
                "새 친구가 생겼어요", "철수님과 친구가 됐어요",
                Map.of("actorId", CHULSU.toString(), "notificationId", "912"), null));
    }

    @Test
    @DisplayName("초대 알림은 시작 시각과 방을 싣고 모집 마감에 만료된다")
    void sendsRunningInvited() {
        // given
        givenItem(item(UserNotificationType.RUNNING_INVITED, actor(false), invitation(RoomInvitationStatus.PENDING)));

        // when
        handler.handle(COMMAND);

        // then -> 마감 뒤에는 수락할 수 없다
        assertThat(sentCommand().message()).isEqualTo(new PushMessage(PushType.RUNNING_INVITED,
                "러닝 초대가 왔어요", "철수님이 21:30 러닝에 초대했어요",
                Map.of("actorId", CHULSU.toString(), "notificationId", "912", "runningRoomId", "130"),
                START_AT.minus(CLOSE_OFFSET)));
    }

    @Test
    @DisplayName("보낸 사람이 그새 탈퇴했으면 보내지 않는다")
    void skipsDeletedActor() {
        // given -> "탈퇴한 사용자님이…"가 된다
        givenItem(item(UserNotificationType.FRIEND_REQUESTED, actor(true), null));

        // when
        handler.handle(COMMAND);

        // then
        verifyNoInteractions(sendPushUsecase);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = RoomInvitationStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("초대가 그새 대기가 아니게 됐으면 보내지 않는다")
    void skipsAnsweredInvitation(RoomInvitationStatus status) {
        // given
        givenItem(item(UserNotificationType.RUNNING_INVITED, actor(false), invitation(status)));

        // when
        handler.handle(COMMAND);

        // then
        verifyNoInteractions(sendPushUsecase);
    }

    @Test
    @DisplayName("알림을 읽지 못하면 보내지 않는다")
    void skipsMissingNotification() {
        // given
        given(userNotificationItemReader.read(new UserId(ME), NOTIFICATION_ID)).willReturn(Optional.empty());

        // when
        handler.handle(COMMAND);

        // then
        verifyNoInteractions(sendPushUsecase);
    }

    private void givenItem(GetUserNotificationsResult.Item item) {
        given(userNotificationItemReader.read(new UserId(ME), NOTIFICATION_ID)).willReturn(Optional.of(item));
    }

    private SendPushCommand sentCommand() {
        ArgumentCaptor<SendPushCommand> captor = ArgumentCaptor.forClass(SendPushCommand.class);
        verify(sendPushUsecase).handle(captor.capture());
        return captor.getValue();
    }

    private static GetUserNotificationsResult.Item item(UserNotificationType type,
                                                        GetUserNotificationsResult.Actor actor,
                                                        GetUserNotificationsResult.Invitation invitation) {
        return new GetUserNotificationsResult.Item(
                NOTIFICATION_ID, type, false, LocalDateTime.of(2026, 10, 29, 12, 0), actor, invitation);
    }

    private static GetUserNotificationsResult.Actor actor(boolean deleted) {
        return deleted
                ? new GetUserNotificationsResult.Actor(CHULSU, "탈퇴한 사용자", null, true, null)
                : new GetUserNotificationsResult.Actor(CHULSU, "철수", null, false, RelationshipType.FRIEND);
    }

    private static GetUserNotificationsResult.Invitation invitation(RoomInvitationStatus status) {
        return new GetUserNotificationsResult.Invitation(ROOM_ID, status, START_AT, 5_000);
    }
}
