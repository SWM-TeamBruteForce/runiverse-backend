package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationCommand;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationHandler;
import com.runiverse.running_service.application.match.command.inviteaccept.AcceptInvitationResult;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.exception.InvitationNotFoundException;
import com.runiverse.running_service.application.match.exception.RunningInvitationClosedException;
import com.runiverse.running_service.application.match.exception.RunningInvitationNotPendingException;
import com.runiverse.running_service.application.match.exception.RunningRoomFullException;
import com.runiverse.running_service.application.match.port.out.CreateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.ExistsActiveApplicationPort;
import com.runiverse.running_service.application.match.port.out.LockMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.LoadMatchPlayersPort;
import com.runiverse.running_service.application.match.port.out.MatchPlayer;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("초대 수락 단위 테스트")
class AcceptInvitationHandlerTest {

    private static final long ROOM_ID = 130L;
    private static final long NEW_PLAYER_ID = 77L;
    private static final int HOST_PACE = 300;
    private static final int INVITEE_PACE = 360;
    private static final int TARGET_DISTANCE = 7_000;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ofHours(6),
            10, Duration.ofMinutes(20),
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
    private static final InviteProperties INVITE_PROPERTIES = new InviteProperties(
            Duration.ofMinutes(10), CLOSE_OFFSET, 1_000, 42_195, 5);

    @Mock
    private LockMatchRoomPort lockMatchRoomPort;

    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;

    @Mock
    private ExistsActiveApplicationPort existsActiveApplicationPort;

    @Mock
    private LoadUserAvgPacePort loadUserAvgPacePort;

    @Mock
    private CreateMatchApplicationPort createMatchApplicationPort;

    @Mock
    private LoadMatchPlayersPort loadMatchPlayersPort;

    @Mock
    private RoomInfoAssembler roomInfoAssembler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AcceptInvitationHandler handler;
    private UserId host;
    private UserId otherInviter;
    private UserId invitee;
    private LocalDateTime startAt;

    @BeforeEach
    void setUp() {
        handler = new AcceptInvitationHandler(
                lockMatchRoomPort, updateMatchRoomPort,
                new InviteStartPolicy(INVITE_PROPERTIES, MATCH_PROPERTIES),
                existsActiveApplicationPort, loadUserAvgPacePort, createMatchApplicationPort,
                loadMatchPlayersPort, roomInfoAssembler, eventPublisher);
        host = newUser();
        otherInviter = newUser();
        invitee = newUser();
        // 핸들러가 지금 시각을 직접 읽는다 — 마감(시작 1분 전)이 한참 남은 방으로 잡는다
        startAt = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0);
    }

    private static UserId newUser() {
        return new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    private RunningRoom room(RunningRoomStatus status, LocalDateTime startAt, int members,
                             RoomInvitationStatus invitationStatus) {
        List<SessionDraft> sessions = new ArrayList<>();
        sessions.add(new SessionDraft(host, new RunningPlayerId(1L), 0, true));
        for (int i = 1; i < members; i++) {
            sessions.add(new SessionDraft(newUser(), new RunningPlayerId(10L + i), 0, true));
        }
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.INVITE)
                .status(status)
                .startAt(startAt)
                .closeAt(status.isTerminal() ? LocalDateTime.now() : null)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(HOST_PACE)
                .currentPlayerCount(status == RunningRoomStatus.CANCELLED ? 0 : members)
                .maxPlayerCount(4)
                .sessions(sessions)
                .invitations(invitationStatus == null ? List.of() : List.of(
                        new InvitationDraft(host, invitee, invitationStatus, 1),
                        new InvitationDraft(otherInviter, invitee, invitationStatus, 2)))
                .build();
    }

    private RunningRoom pendingRoom(int members) {
        return room(RunningRoomStatus.MATCHING, startAt, members, RoomInvitationStatus.PENDING);
    }

    private void givenLocked(RunningRoom room) {
        given(lockMatchRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private void givenJoinable() {
        given(existsActiveApplicationPort.existsActive(invitee)).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(invitee)).willReturn(Optional.of(new Pace(INVITEE_PACE)));
        given(createMatchApplicationPort.create(any())).willReturn(RunningPlayer.builder()
                .runningPlayerId(NEW_PLAYER_ID)
                .userId(invitee.value())
                .avgPace(INVITEE_PACE)
                .targetDistance(TARGET_DISTANCE)
                .startAt(startAt)
                .build());
        given(loadMatchPlayersPort.loadPlayers(new RunningRoomId(ROOM_ID)))
                .willReturn(List.of(new MatchPlayer(host.value(), HOST_PACE)));
        given(roomInfoAssembler.assemble(any())).willReturn(new RoomInfo(
                ROOM_ID, RunningRoomType.INVITE, RunningRoomStatus.MATCHING, startAt, startAt.minus(CLOSE_OFFSET),
                TARGET_DISTANCE, HOST_PACE, List.of(), List.of()));
    }

    private AcceptInvitationResult accept() {
        return handler.handle(new AcceptInvitationCommand(invitee.value(), ROOM_ID));
    }

    @Nested
    @DisplayName("성공 테스트")
    class SuccessTest {

        @Test
        @DisplayName("수락하면 방 시각·거리로 신청을 만들고 자리를 잡아 받은 행을 모두 수락으로 바꾼다")
        void acceptInvitation() {
            // given
            RunningRoom room = pendingRoom(1);
            givenLocked(room);
            givenJoinable();

            // when
            AcceptInvitationResult result = accept();

            // then
            assertThat(result.runningRoomId()).isEqualTo(ROOM_ID);
            ArgumentCaptor<RunningPlayer> created = ArgumentCaptor.forClass(RunningPlayer.class);
            verify(createMatchApplicationPort).create(created.capture());
            assertThat(created.getValue().getUserId().value()).isEqualTo(invitee.value());
            assertThat(created.getValue().getStatus()).isEqualTo(RunningPlayerStatus.JOINED);
            assertThat(created.getValue().getStartAt()).isEqualTo(startAt);
            assertThat(created.getValue().getTargetDistance().meters()).isEqualTo(TARGET_DISTANCE);
            assertThat(room.getPlayerCount().current()).isEqualTo(2);
            assertThat(room.getSessions()).anySatisfy(session -> {
                assertThat(session.isSameUser(invitee)).isTrue();
                assertThat(session.isSamePlayer(new RunningPlayerId(NEW_PLAYER_ID))).isTrue();
            });
            // 누가 보냈든 받는 사람 기준이다
            assertThat(room.getInvitations()).extracting(RoomInvitation::getStatus)
                    .containsOnly(RoomInvitationStatus.ACCEPTED);
            verify(updateMatchRoomPort).update(room);
            verify(eventPublisher).publishEvent(any(MatchRoomChangedEvent.class));
        }

        @Test
        @DisplayName("방 평균 페이스는 기존 참가자에 합류자를 더해 다시 계산한다")
        void recalculatesAvgPace() {
            // given
            RunningRoom room = pendingRoom(1);
            givenLocked(room);
            givenJoinable();

            // when
            accept();

            // then -> (300 + 360) / 2
            assertThat(room.getAvgPace()).hasValueSatisfying(pace ->
                    assertThat(pace.secondsPerKm()).isEqualTo(330));
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
            assertThatThrownBy(AcceptInvitationHandlerTest.this::accept)
                    .isInstanceOf(InvitationNotFoundException.class);
            verifyNoInteractions(createMatchApplicationPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("이 방에서 나에게 온 초대가 없으면 찾을 수 없다")
        void rejectsNotInvited() {
            // given
            givenLocked(room(RunningRoomStatus.MATCHING, startAt, 1, null));

            // when & then
            assertThatThrownBy(AcceptInvitationHandlerTest.this::accept)
                    .isInstanceOf(InvitationNotFoundException.class);
            verifyNoInteractions(createMatchApplicationPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("만료된 초대는 닫힌 것으로 거절한다")
        void rejectsExpired() {
            // given -> 만료는 응답이 아니라 방이 닫힌 것이다
            givenLocked(room(RunningRoomStatus.MATCHING, startAt, 1, RoomInvitationStatus.EXPIRED));

            // when & then
            assertThatThrownBy(AcceptInvitationHandlerTest.this::accept)
                    .isInstanceOf(RunningInvitationClosedException.class);
            verifyNoInteractions(existsActiveApplicationPort, createMatchApplicationPort, updateMatchRoomPort);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RunningRoomStatus.class, names = "MATCHING", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("모집 중이 아닌 방은 대기 중 초대라도 닫힌 것으로 거절한다")
        void rejectsNotRecruiting(RunningRoomStatus status) {
            // given
            givenLocked(room(status, startAt, 1, RoomInvitationStatus.PENDING));

            // when & then
            assertThatThrownBy(AcceptInvitationHandlerTest.this::accept)
                    .isInstanceOf(RunningInvitationClosedException.class);
            verifyNoInteractions(createMatchApplicationPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("마감 시각이 지났으면 모집 중으로 남아 있어도 닫힌 것으로 거절한다")
        void rejectsPastCloseAt() {
            // given -> 시작 30초 전이면 마감(1분 전)이 이미 지났다
            givenLocked(room(RunningRoomStatus.MATCHING, LocalDateTime.now().plusSeconds(30), 1,
                    RoomInvitationStatus.PENDING));

            // when & then
            assertThatThrownBy(AcceptInvitationHandlerTest.this::accept)
                    .isInstanceOf(RunningInvitationClosedException.class);
            verifyNoInteractions(createMatchApplicationPort, updateMatchRoomPort);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = RoomInvitationStatus.class, names = {"ACCEPTED", "DECLINED", "LEFT"})
        @DisplayName("이미 응답한 초대는 거절한다")
        void rejectsAnswered(RoomInvitationStatus status) {
            // given -> 거절 뒤 들어가려면 다시 초대받아야 한다
            givenLocked(room(RunningRoomStatus.MATCHING, startAt, 1, status));

            // when & then
            assertThatThrownBy(AcceptInvitationHandlerTest.this::accept)
                    .isInstanceOf(RunningInvitationNotPendingException.class);
            verifyNoInteractions(existsActiveApplicationPort, createMatchApplicationPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("자리가 다 찼으면 거절하고 초대는 대기로 남는다")
        void rejectsFullRoom() {
            // given
            RunningRoom room = pendingRoom(4);
            givenLocked(room);

            // when & then -> 신청 행을 만들기 전에 끝난다
            assertThatThrownBy(AcceptInvitationHandlerTest.this::accept)
                    .isInstanceOf(RunningRoomFullException.class);
            assertThat(room.invitationStatusOf(invitee)).contains(RoomInvitationStatus.PENDING);
            verifyNoInteractions(existsActiveApplicationPort, createMatchApplicationPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("다른 활성 신청이 있으면 거절한다")
        void rejectsActiveApplication() {
            // given -> 초대받은 사이 매칭을 시작했다
            givenLocked(pendingRoom(1));
            given(existsActiveApplicationPort.existsActive(invitee)).willReturn(true);

            // when & then
            assertThatThrownBy(AcceptInvitationHandlerTest.this::accept)
                    .isInstanceOf(AlreadyRunningException.class);
            verifyNoInteractions(loadUserAvgPacePort, createMatchApplicationPort, updateMatchRoomPort);
        }

        @Test
        @DisplayName("온보딩 전이면 쓸 페이스가 없어 거절한다")
        void rejectsWithoutOnboarding() {
            // given
            givenLocked(pendingRoom(1));
            given(existsActiveApplicationPort.existsActive(invitee)).willReturn(false);
            given(loadUserAvgPacePort.loadAvgPace(invitee)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(AcceptInvitationHandlerTest.this::accept)
                    .isInstanceOf(OnboardingNotCompletedException.class);
            verifyNoInteractions(createMatchApplicationPort, updateMatchRoomPort, eventPublisher);
        }
    }
}
