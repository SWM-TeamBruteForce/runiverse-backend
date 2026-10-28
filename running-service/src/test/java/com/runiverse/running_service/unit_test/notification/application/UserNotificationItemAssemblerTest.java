package com.runiverse.running_service.unit_test.notification.application;

import ch.qos.logback.classic.Level;
import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadPlayerProfilesPort;
import com.runiverse.running_service.application.common.port.out.LoadRelationshipTypesPort;
import com.runiverse.running_service.application.common.port.out.PlayerProfile;
import com.runiverse.running_service.application.notification.common.UserNotificationItemAssembler;
import com.runiverse.running_service.application.notification.port.out.InvitationSnapshot;
import com.runiverse.running_service.application.notification.port.out.LoadInvitationSnapshotsPort;
import com.runiverse.running_service.application.notification.port.out.UserNotificationRow;
import com.runiverse.running_service.application.notification.query.list.GetUserNotificationsResult;
import com.runiverse.running_service.application.user.port.out.GenerateViewUrlPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.notification.vo.FriendRequestedPayload;
import com.runiverse.running_service.domain.notification.vo.RunningInvitedPayload;
import com.runiverse.running_service.domain.notification.vo.UserNotificationType;
import com.runiverse.running_service.domain.relationship.vo.RelationshipType;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 항목 조립 단위 테스트")
class UserNotificationItemAssemblerTest {

    private static final UserId ME = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UUID CHULSU = UuidCreator.getTimeOrderedEpoch();
    private static final long ROOM_ID = 130L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 19, 30);

    @Mock
    private LoadPlayerProfilesPort loadPlayerProfilesPort;

    @Mock
    private LoadRelationshipTypesPort loadRelationshipTypesPort;

    @Mock
    private LoadInvitationSnapshotsPort loadInvitationSnapshotsPort;

    @Mock
    private GenerateViewUrlPort generateViewUrlPort;

    private UserNotificationItemAssembler assembler;
    private LogCapture log;

    @BeforeEach
    void setUp() {
        assembler = new UserNotificationItemAssembler(
                loadPlayerProfilesPort, loadRelationshipTypesPort, loadInvitationSnapshotsPort, generateViewUrlPort);
        log = LogCapture.of(UserNotificationItemAssembler.class);
    }

    @AfterEach
    void tearDown() {
        log.stop();
    }

    private static UserNotificationRow friendRequested(long id) {
        return new UserNotificationRow(id, new FriendRequestedPayload(new UserId(CHULSU)), false,
                LocalDateTime.of(2026, 10, 28, 12, 0));
    }

    private static UserNotificationRow invited(long id) {
        return new UserNotificationRow(id,
                new RunningInvitedPayload(new UserId(CHULSU), new RunningRoomId(ROOM_ID)), true,
                LocalDateTime.of(2026, 10, 28, 13, 0));
    }

    private void givenChulsuAlive(String profileImageKey) {
        given(loadPlayerProfilesPort.loadProfiles(Set.of(CHULSU)))
                .willReturn(Map.of(CHULSU, new PlayerProfile(CHULSU, "철수", profileImageKey, null)));
    }

    @Test
    @DisplayName("친구 요청 알림에 일으킨 사람의 프로필과 지금 관계를 붙이고, 초대는 읽지 않는다")
    void assemblesFriendRequestedItem() {
        // given
        givenChulsuAlive("profile/chulsu.jpg");
        given(generateViewUrlPort.generate("profile/chulsu.jpg")).willReturn("https://cdn/chulsu.jpg");
        given(loadRelationshipTypesPort.loadTypes(ME, Set.of(CHULSU)))
                .willReturn(Map.of(CHULSU, RelationshipType.FRIEND_REQUEST_RECEIVED));

        // when
        List<GetUserNotificationsResult.Item> items = assembler.assemble(ME, List.of(friendRequested(912L)));

        // then
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.notificationId()).isEqualTo(912L);
            assertThat(item.type()).isEqualTo(UserNotificationType.FRIEND_REQUESTED);
            assertThat(item.read()).isFalse();
            assertThat(item.actor()).isEqualTo(new GetUserNotificationsResult.Actor(
                    CHULSU, "철수", "https://cdn/chulsu.jpg", false, RelationshipType.FRIEND_REQUEST_RECEIVED));
            assertThat(item.invitation()).isNull();
        });
        verifyNoInteractions(loadInvitationSnapshotsPort);
    }

    @Test
    @DisplayName("초대 알림에는 받은 초대의 지금 상태와 방 정보를 붙인다")
    void assemblesInvitationItem() {
        // given
        givenChulsuAlive(null);
        given(loadRelationshipTypesPort.loadTypes(ME, Set.of(CHULSU))).willReturn(Map.of(CHULSU, RelationshipType.FRIEND));
        given(loadInvitationSnapshotsPort.loadSnapshots(ME, Set.of(ROOM_ID))).willReturn(Map.of(
                ROOM_ID, new InvitationSnapshot(ROOM_ID, RoomInvitationStatus.EXPIRED, START, 7_000)));

        // when
        List<GetUserNotificationsResult.Item> items = assembler.assemble(ME, List.of(invited(913L)));

        // then -> 알림은 사건의 기록이고 버튼은 지금 상태(EXPIRED)로 그린다
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(UserNotificationType.RUNNING_INVITED);
            assertThat(item.invitation()).isEqualTo(new GetUserNotificationsResult.Invitation(
                    ROOM_ID, RoomInvitationStatus.EXPIRED, START, 7_000));
            assertThat(item.actor().relationshipType()).isEqualTo(RelationshipType.FRIEND);
            assertThat(item.actor().profileImageUrl()).isNull();
        });
        verifyNoInteractions(generateViewUrlPort);
    }

    @Test
    @DisplayName("일으킨 사람이 탈퇴했으면 탈퇴 유저 형식으로 싣고 관계는 싣지 않는다")
    void marksDeletedActor() {
        // given -> 탈퇴하면 users 행이 지워져 프로필이 나오지 않는다
        given(loadPlayerProfilesPort.loadProfiles(Set.of(CHULSU))).willReturn(Map.of());
        given(loadRelationshipTypesPort.loadTypes(ME, Set.of(CHULSU))).willReturn(Map.of());

        // when
        List<GetUserNotificationsResult.Item> items = assembler.assemble(ME, List.of(friendRequested(912L)));

        // then -> 알림은 남고 사람만 탈퇴 유저 형식이다
        assertThat(items).singleElement().extracting(GetUserNotificationsResult.Item::actor)
                .isEqualTo(new GetUserNotificationsResult.Actor(CHULSU, "탈퇴한 사용자", null, true, null));
    }

    @Test
    @DisplayName("관계 행이 없는 사람은 NONE으로 본다")
    void missingRelationshipIsNone() {
        // given
        givenChulsuAlive(null);
        given(loadRelationshipTypesPort.loadTypes(ME, Set.of(CHULSU))).willReturn(Map.of());

        // when & then
        assertThat(assembler.assemble(ME, List.of(friendRequested(912L)))).singleElement()
                .extracting(item -> item.actor().relationshipType()).isEqualTo(RelationshipType.NONE);
    }

    @Test
    @DisplayName("초대 행이 사라진 초대 알림은 그 항목만 빼고 경고를 남긴다")
    void skipsInvitationWithoutRow() {
        // given
        givenChulsuAlive(null);
        given(loadRelationshipTypesPort.loadTypes(ME, Set.of(CHULSU))).willReturn(Map.of());
        given(loadInvitationSnapshotsPort.loadSnapshots(ME, Set.of(ROOM_ID))).willReturn(Map.of());

        // when
        List<GetUserNotificationsResult.Item> items = assembler.assemble(ME, List.of(friendRequested(912L), invited(913L)));

        // then -> 알림 하나 때문에 알림함 전체가 막히지 않는다
        assertThat(items).extracting(GetUserNotificationsResult.Item::notificationId).containsExactly(912L);
        assertThat(log.messages(Level.WARN)).containsExactly(
                "[알림] 목록 항목 건너뜀: 초대 행 없음 - notificationId=913, runningRoomId=" + ROOM_ID);
    }

    @Test
    @DisplayName("행이 없으면 사람·관계·초대를 읽지 않는다")
    void emptyRowsSkipLookups() {
        // when
        List<GetUserNotificationsResult.Item> items = assembler.assemble(ME, List.of());

        // then
        assertThat(items).isEmpty();
        verifyNoInteractions(loadPlayerProfilesPort, loadRelationshipTypesPort, loadInvitationSnapshotsPort);
    }
}
