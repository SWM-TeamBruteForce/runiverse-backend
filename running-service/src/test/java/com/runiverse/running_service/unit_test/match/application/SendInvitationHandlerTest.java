package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.invitesend.SendInvitationCommand;
import com.runiverse.running_service.application.match.command.invitesend.SendInvitationHandler;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.exception.InviteCountExceededException;
import com.runiverse.running_service.application.match.exception.InviteRoomNotFoundException;
import com.runiverse.running_service.application.match.exception.InviteeAlreadyInRoomException;
import com.runiverse.running_service.application.match.exception.InviteeInMatchingException;
import com.runiverse.running_service.application.match.exception.InviteeRunningException;
import com.runiverse.running_service.application.match.exception.NotFriendException;
import com.runiverse.running_service.application.match.exception.RunningInvitationClosedException;
import com.runiverse.running_service.application.match.port.out.CheckFriendPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.running.port.out.LoadUserStatusPort;
import com.runiverse.running_service.application.running.port.out.UserStatusRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.room.InvitationDraft;
import com.runiverse.running_service.domain.running.room.RoomInvitation;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RoomInvitationStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 한 번에 한 명이다 — 못 보내면 그 사람이 왜 안 되는지를 409로 알린다
@ExtendWith(MockitoExtension.class)
@DisplayName("친구 초대 단위 테스트")
class SendInvitationHandlerTest {

    private static final long ROOM_ID = 130L;
    private static final int PACE = 330;
    private static final int TARGET_DISTANCE = 7_000;
    private static final int MAX_INVITE_COUNT = 5;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofHours(6), 10, Duration.ofMinutes(20),
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
    private static final InviteProperties INVITE_PROPERTIES = new InviteProperties(
            Duration.ofMinutes(10), CLOSE_OFFSET, 1_000, 42_195, MAX_INVITE_COUNT);

    @Mock
    private LockMatchRoomPort lockMatchRoomPort;

    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;

    @Mock
    private CheckFriendPort checkFriendPort;

    @Mock
    private LoadUserStatusPort loadUserStatusPort;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SendInvitationHandler handler;
    private UserId inviter;
    private UserId friend;

    @BeforeEach
    void setUp() {
        handler = new SendInvitationHandler(
                lockMatchRoomPort, updateMatchRoomPort, checkFriendPort, loadUserStatusPort,
                new InviteStartPolicy(INVITE_PROPERTIES, MATCH_PROPERTIES), INVITE_PROPERTIES,
                roomInfoAssembler, eventPublisher);
        inviter = newUser();
        friend = newUser();
    }

    private static UserId newUser() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    // 핸들러가 지금 시각을 직접 읽어 시작 시각을 지금 기준으로 잡는다 — 마감은 시작 1분 전이다
    private static LocalDateTime openStart() {
        return LocalDateTime.now().plusHours(1);
    }

    private RunningRoom room(RunningRoomType type, RunningRoomStatus status, LocalDateTime startAt,
                             List<SessionDraft> sessions, List<InvitationDraft> invitations) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(type)
                .status(status)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(PACE)
                .currentPlayerCount((int) sessions.stream().filter(SessionDraft::connected).count())
                .maxPlayerCount(4)
                .sessions(sessions)
                .invitations(invitations)
                .build();
    }

    private RunningRoom recruitingRoom(List<InvitationDraft> invitations) {
        return room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING, openStart(),
                List.of(new SessionDraft(inviter, new RunningPlayerId(1L), 0, true)), invitations);
    }

    private void givenLocked(RunningRoom room) {
        given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private void givenFriend(boolean friendship) {
        given(checkFriendPort.isFriend(inviter, friend)).willReturn(friendship);
    }

    private void givenIdle() {
        given(loadUserStatusPort.loadStatus(friend)).willReturn(Optional.empty());
    }

    private void givenActive(RunningRoomType type, RunningRoomStatus roomStatus) {
        given(loadUserStatusPort.loadStatus(friend)).willReturn(Optional.of(
                new UserStatusRow(999L, type, roomStatus, openStart(), TARGET_DISTANCE)));
    }

    private void givenAssembled() {
        given(roomInfoAssembler.assemble(any())).willReturn(new RoomInfo(
                ROOM_ID, RunningRoomType.INVITE, RunningRoomStatus.MATCHING, openStart(),
                openStart().minus(CLOSE_OFFSET), TARGET_DISTANCE, PACE, List.of(), List.of()));
    }

    private void invite() {
        handler.handle(new SendInvitationCommand(inviter.value(), ROOM_ID, friend.value()));
    }

    private RoomInvitation rowOf(RunningRoom room) {
        return room.getInvitations().stream()
                .filter(invitation -> invitation.isFor(friend) && invitation.isSentBy(inviter))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("성공 테스트")
    class SuccessTest {

        @Test
        @DisplayName("친구에게 처음 보내면 대기 초대가 1회로 생기고 방을 저장한 뒤 갱신을 알린다")
        void inviteFriend() {
            // given
            RunningRoom room = recruitingRoom(List.of());
            givenLocked(room);
            givenFriend(true);
            givenIdle();
            givenAssembled();

            // when
            invite();

            // then
            assertThat(rowOf(room).getStatus()).isEqualTo(RoomInvitationStatus.PENDING);
            assertThat(rowOf(room).getInviteCount().value()).isEqualTo(1);
            verify(updateMatchRoomPort).update(room);
            verify(eventPublisher).publishEvent(any(MatchRoomChangedEvent.class));
        }

        @Test
        @DisplayName("대기 중에 다시 보내면 상태는 대기 그대로이고 보낸 횟수가 이어서 오른다")
        void resendKeepsCounting() {
            // given
            RunningRoom room = recruitingRoom(List.of(
                    new InvitationDraft(inviter, friend, RoomInvitationStatus.PENDING, 2)));
            givenLocked(room);
            givenFriend(true);
            givenIdle();
            givenAssembled();

            // when
            invite();

            // then
            assertThat(rowOf(room).getInviteCount().value()).isEqualTo(3);
            assertThat(rowOf(room).getStatus()).isEqualTo(RoomInvitationStatus.PENDING);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RoomInvitationStatus.class, names = {"DECLINED", "LEFT"})
        @DisplayName("거절했거나 나간 친구는 대기로 되살아난다")
        void revivesAnswered(RoomInvitationStatus status) {
            // given
            RunningRoom room = recruitingRoom(List.of(new InvitationDraft(inviter, friend, status, 1)));
            givenLocked(room);
            givenFriend(true);
            givenIdle();
            givenAssembled();

            // when
            invite();

            // then
            assertThat(rowOf(room).getStatus()).isEqualTo(RoomInvitationStatus.PENDING);
            assertThat(rowOf(room).getInviteCount().value()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("실패 테스트")
    class FailureTest {

        @Test
        @DisplayName("방이 없으면 찾을 수 없다")
        void rejectsMissingRoom() {
            // given
            given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite).isInstanceOf(InviteRoomNotFoundException.class);
            verifyNoInteractions(checkFriendPort, loadUserStatusPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("매칭 방이면 찾을 수 없다")
        void rejectsMatchRoom() {
            // given -> 초대 방이 아니면 있는지도 알려 주지 않는다
            givenLocked(room(RunningRoomType.MATCH, RunningRoomStatus.MATCHING, openStart(),
                    List.of(new SessionDraft(inviter, new RunningPlayerId(1L), 0, true)), List.of()));

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite).isInstanceOf(InviteRoomNotFoundException.class);
            verifyNoInteractions(checkFriendPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("호출자가 이 방 참가자가 아니면 찾을 수 없다")
        void rejectsStranger() {
            // given
            givenLocked(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING, openStart(),
                    List.of(new SessionDraft(newUser(), new RunningPlayerId(1L), 0, true)), List.of()));

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite).isInstanceOf(InviteRoomNotFoundException.class);
            verifyNoInteractions(checkFriendPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("방을 나간 참가자는 찾을 수 없다")
        void rejectsLeftMember() {
            // given
            givenLocked(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING, openStart(), List.of(
                    new SessionDraft(newUser(), new RunningPlayerId(2L), 0, true),
                    new SessionDraft(inviter, new RunningPlayerId(1L), 1, false)), List.of()));

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite).isInstanceOf(InviteRoomNotFoundException.class);
        }

        @Test
        @DisplayName("모집이 끝난 방에는 초대할 수 없다")
        void rejectsClosedStatus() {
            // given
            givenLocked(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHED, openStart(),
                    List.of(new SessionDraft(inviter, new RunningPlayerId(1L), 0, true)), List.of()));

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite)
                    .isInstanceOf(RunningInvitationClosedException.class);
            verifyNoInteractions(checkFriendPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("모집 중이어도 마감 시각이 지났으면 초대할 수 없다")
        void rejectsPastCloseAt() {
            // given -> 시작 30초 전이면 마감(1분 전)이 이미 지났다. 마감 예약이 아직 안 돈 상황이다
            givenLocked(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING,
                    LocalDateTime.now().plusSeconds(30),
                    List.of(new SessionDraft(inviter, new RunningPlayerId(1L), 0, true)), List.of()));

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite)
                    .isInstanceOf(RunningInvitationClosedException.class);
            verifyNoInteractions(checkFriendPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("친구가 아니면 409이고 아무것도 바꾸지 않는다")
        void rejectsNonFriend() {
            // given
            RunningRoom room = recruitingRoom(List.of());
            givenLocked(room);
            givenFriend(false);

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite).isInstanceOf(NotFriendException.class);
            assertThat(room.getInvitations()).isEmpty();
            verifyNoInteractions(loadUserStatusPort, updateMatchRoomPort, eventPublisher);
        }

        @Test
        @DisplayName("자기 자신을 넣으면 친구가 아니라 409로 끝난다")
        void rejectsSelf() {
            // given -> 도메인의 자기 초대 예외(500)까지 가지 않는다
            givenLocked(recruitingRoom(List.of()));
            given(checkFriendPort.isFriend(inviter, inviter)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> handler.handle(new SendInvitationCommand(inviter.value(), ROOM_ID, inviter.value())))
                    .isInstanceOf(NotFriendException.class);
            verifyNoInteractions(updateMatchRoomPort);
        }

        @Test
        @DisplayName("친구가 이미 이 방에 있으면 매칭 중이 아니라 이미 방에 있다고 알린다")
        void rejectsAlreadyInRoom() {
            // given -> 이 친구도 활성 신청이 있지만, 같은 방이라는 더 정확한 이유가 먼저다
            givenLocked(room(RunningRoomType.INVITE, RunningRoomStatus.MATCHING, openStart(), List.of(
                    new SessionDraft(inviter, new RunningPlayerId(1L), 0, true),
                    new SessionDraft(friend, new RunningPlayerId(2L), 0, true)), List.of()));
            givenFriend(true);

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite)
                    .isInstanceOf(InviteeAlreadyInRoomException.class);
            verifyNoInteractions(loadUserStatusPort, updateMatchRoomPort);
        }

        @ParameterizedTest(name = "{0}·{1}")
        @CsvSource({"MATCH,MATCHING", "MATCH,MATCHED", "INVITE,MATCHING", "INVITE,MATCHED"})
        @DisplayName("다른 매칭 방·초대 방에서 대기 중이면 매칭 중이라고 알린다")
        void rejectsInMatching(RunningRoomType type, RunningRoomStatus roomStatus) {
            // given
            givenLocked(recruitingRoom(List.of()));
            givenFriend(true);
            givenActive(type, roomStatus);

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite).isInstanceOf(InviteeInMatchingException.class);
            verifyNoInteractions(updateMatchRoomPort, eventPublisher);
        }

        @ParameterizedTest(name = "{0}·{1}")
        @CsvSource({"MATCH,STARTED", "INVITE,STARTED", "SOLO,MATCHED", "SOLO,STARTED"})
        @DisplayName("시작된 방에 있거나 솔로 방을 열었으면 러닝 중이라고 알린다")
        void rejectsRunning(RunningRoomType type, RunningRoomStatus roomStatus) {
            // given -> 솔로는 모집 단계가 없어 시작 전(MATCHED)이어도 러닝으로 본다
            givenLocked(recruitingRoom(List.of()));
            givenFriend(true);
            givenActive(type, roomStatus);

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite).isInstanceOf(InviteeRunningException.class);
            verifyNoInteractions(updateMatchRoomPort, eventPublisher);
        }

        @Test
        @DisplayName("이 친구에게 보낸 횟수가 상한에 닿았으면 409이고 상태도 바꾸지 않는다")
        void rejectsCountExceeded() {
            // given -> 거절당한 뒤 계속 보내는 것을 막는다
            RunningRoom room = recruitingRoom(List.of(new InvitationDraft(
                    inviter, friend, RoomInvitationStatus.DECLINED, MAX_INVITE_COUNT)));
            givenLocked(room);
            givenFriend(true);
            givenIdle();

            // when & then
            assertThatThrownBy(SendInvitationHandlerTest.this::invite).isInstanceOf(InviteCountExceededException.class);
            assertThat(rowOf(room).getStatus()).isEqualTo(RoomInvitationStatus.DECLINED);
            assertThat(rowOf(room).getInviteCount().value()).isEqualTo(MAX_INVITE_COUNT);
            verifyNoInteractions(updateMatchRoomPort, eventPublisher);
        }
    }
}
