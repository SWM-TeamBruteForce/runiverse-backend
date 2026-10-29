package com.runiverse.running_service.unit_test.match.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.common.port.out.LoadUserAvgPacePort;
import com.runiverse.running_service.application.common.port.out.ScheduleJobPort;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomCommand;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomHandler;
import com.runiverse.running_service.application.match.command.inviteopen.OpenInviteRoomResult;
import com.runiverse.running_service.application.match.common.InviteProperties;
import com.runiverse.running_service.application.match.common.InviteStartPolicy;
import com.runiverse.running_service.application.match.exception.InvalidInviteOptionException;
import com.runiverse.running_service.application.match.exception.InviteStartAtOutOfRangeException;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.port.out.CreateMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.CreateMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.ExistsActiveApplicationPort;
import com.runiverse.running_service.application.running.exception.AlreadyRunningException;
import com.runiverse.running_service.application.user.exception.OnboardingNotCompletedException;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Pace;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RoomSession;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.domain.scheduling.vo.ScheduledJobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("초대 방 개설 단위 테스트")
class OpenInviteRoomHandlerTest {

    private static final int AVG_PACE = 330;          // 5분 30초/km
    private static final int TARGET_DISTANCE = 7_000;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 26, 19, 0);
    private static final long PLAYER_ID = 42L;
    private static final long ROOM_ID = 130L;
    private static final Duration CLOSE_OFFSET = Duration.ofMinutes(1);
    private static final Duration REMINDER_OFFSET = Duration.ofMinutes(5);
    private static final Duration READY_OFFSET = Duration.ofSeconds(10);
    private static final Duration FORCE_FINISH_OFFSET = Duration.ofHours(6);
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), REMINDER_OFFSET, READY_OFFSET, FORCE_FINISH_OFFSET, 10, Duration.ofMinutes(20),
            LocalTime.of(18, 0), LocalTime.of(22, 0), Duration.ofMinutes(30), List.of(3_000, 5_000, 10_000));
    private static final InviteProperties INVITE_PROPERTIES = new InviteProperties(
            Duration.ofMinutes(10), CLOSE_OFFSET, 1_000, 42_195, 5);

    @Mock
    private ExistsActiveApplicationPort existsActiveApplicationPort;

    @Mock
    private LoadUserAvgPacePort loadUserAvgPacePort;

    @Mock
    private CreateMatchApplicationPort createMatchApplicationPort;

    @Mock
    private CreateMatchRoomPort createMatchRoomPort;

    @Mock
    private ScheduleJobPort scheduleJobPort;

    // 실제 정책을 감싼 spy — 값 검증(validate)과 마감 계산은 실제로 타고, 시각 범위(isSelectable)만 필요할 때 고정한다.
    // 핸들러가 지금 시각을 직접 읽어 23:49 이후에 돌면 고를 시각이 없기 때문이다. 경계 자체는 InviteStartPolicyTest가 본다
    private InviteStartPolicy inviteStartPolicy;
    private OpenInviteRoomHandler handler;
    private UUID userId;

    @BeforeEach
    void setUp() {
        inviteStartPolicy = spy(new InviteStartPolicy(INVITE_PROPERTIES, MATCH_PROPERTIES));
        handler = new OpenInviteRoomHandler(
                inviteStartPolicy,
                existsActiveApplicationPort, loadUserAvgPacePort,
                createMatchApplicationPort, createMatchRoomPort,
                scheduleJobPort, MATCH_PROPERTIES);
        userId = UuidCreator.getTimeOrderedEpoch();
    }

    private void givenSelectable() {
        doReturn(true).when(inviteStartPolicy).isSelectable(any(), any());
    }

    private static OpenInviteRoomCommand commandAt(UUID userId, LocalDateTime startAt) {
        return new OpenInviteRoomCommand(userId, startAt, TARGET_DISTANCE);
    }

    // 어댑터가 ID를 채워 돌려주는 상황을 흉내 낸다
    private static RunningPlayer savedPlayer(UUID userId, LocalDateTime startAt) {
        return RunningPlayer.builder()
                .runningPlayerId(PLAYER_ID)
                .userId(userId)
                .avgPace(AVG_PACE)
                .targetDistance(TARGET_DISTANCE)
                .startAt(startAt)
                .build();
    }

    private static RunningRoom savedRoom(LocalDateTime startAt) {
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.INVITE)
                .startAt(startAt)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(1)
                .maxPlayerCount(4)
                .build();
    }

    private void givenOpenable(LocalDateTime startAt) {
        givenSelectable();
        given(existsActiveApplicationPort.existsActive(new UserId(userId))).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId))).willReturn(Optional.of(new Pace(AVG_PACE)));
        given(createMatchApplicationPort.create(any())).willReturn(savedPlayer(userId, startAt));
        given(createMatchRoomPort.create(any())).willReturn(savedRoom(startAt));
    }

    @Test
    @DisplayName("초대 방을 열면 방 식별자를 돌려준다")
    void openInviteRoomSuccess() {
        // given
        LocalDateTime startAt = START;
        givenOpenable(startAt);

        // when
        OpenInviteRoomResult result = handler.handle(commandAt(userId, startAt));

        // then -> 클라는 이 ID를 들고 매칭 스트림을 연다
        assertThat(result.runningRoomId()).isEqualTo(ROOM_ID);
    }

    @Test
    @DisplayName("신청은 입력한 시각·거리와 온보딩 페이스로 만들어진다")
    void createsPlayerFromInputAndOnboardingPace() {
        // given
        LocalDateTime startAt = START;
        givenOpenable(startAt);

        // when
        handler.handle(commandAt(userId, startAt));

        // then
        ArgumentCaptor<RunningPlayer> captor = ArgumentCaptor.forClass(RunningPlayer.class);
        verify(createMatchApplicationPort).create(captor.capture());
        RunningPlayer requested = captor.getValue();

        assertThat(requested.getUserId().value()).isEqualTo(userId);
        assertThat(requested.getAvgPace().secondsPerKm()).isEqualTo(AVG_PACE);
        assertThat(requested.getTargetDistance().meters()).isEqualTo(TARGET_DISTANCE);
        assertThat(requested.getStartAt()).isEqualTo(startAt);
        assertThat(requested.getStatus()).isEqualTo(RunningPlayerStatus.JOINED);
    }

    @Test
    @DisplayName("방은 INVITE 종류로 1인 4자리 MATCHING 상태에서 열리고 세션이 저장된 신청을 가리킨다")
    void opensInviteRoomRecruiting() {
        // given
        LocalDateTime startAt = START;
        givenOpenable(startAt);

        // when
        handler.handle(commandAt(userId, startAt));

        // then
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(createMatchRoomPort).create(captor.capture());
        RunningRoom opened = captor.getValue();

        assertThat(opened.getType()).isEqualTo(RunningRoomType.INVITE);
        assertThat(opened.getStatus()).isEqualTo(RunningRoomStatus.MATCHING);
        assertThat(opened.getStartAt()).isEqualTo(startAt);
        assertThat(opened.getTargetDistance().orElseThrow().meters()).isEqualTo(TARGET_DISTANCE);
        assertThat(opened.getPlayerCount().current()).isEqualTo(1);
        assertThat(opened.getPlayerCount().max()).isEqualTo(4);
        RoomSession session = opened.getSessions().getFirst();
        assertThat(session.isSameUser(new UserId(userId))).isTrue();
        assertThat(session.getRunningPlayerId()).isEqualTo(new RunningPlayerId(PLAYER_ID));
    }

    @Test
    @DisplayName("예약 다섯 가지를 걸고 마감만 초대 오프셋을 쓴다")
    void schedulesFiveJobsWithInviteCloseOffset() {
        // given
        LocalDateTime startAt = START;
        givenOpenable(startAt);

        // when
        handler.handle(commandAt(userId, startAt));

        // then -> 마감은 시작 1분 전, 리마인더·통지·시작·강제 종료는 매칭과 같은 시점이다
        verify(scheduleJobPort).schedule(ScheduledJobType.MATCH_CLOSE, ROOM_ID, startAt.minus(CLOSE_OFFSET));
        verify(scheduleJobPort).schedule(ScheduledJobType.RUNNING_REMINDER, ROOM_ID, startAt.minus(REMINDER_OFFSET));
        verify(scheduleJobPort).schedule(ScheduledJobType.RUNNING_READY, ROOM_ID, startAt.minus(READY_OFFSET));
        verify(scheduleJobPort).schedule(ScheduledJobType.RUNNING_START, ROOM_ID, startAt);
        verify(scheduleJobPort).schedule(
                ScheduledJobType.RUNNING_FORCE_FINISH, ROOM_ID, startAt.plus(FORCE_FINISH_OFFSET));
    }

    @Test
    @DisplayName("거리가 범위 밖이면 400으로 거절하고 아무것도 하지 않는다")
    void rejectsInvalidOptionFirst() {
        // given
        OpenInviteRoomCommand command = new OpenInviteRoomCommand(userId, START, 500);

        // when & then
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(InvalidInviteOptionException.class);

        verifyNoInteractions(existsActiveApplicationPort, loadUserAvgPacePort,
                createMatchApplicationPort, createMatchRoomPort, scheduleJobPort);
    }

    @Test
    @DisplayName("초가 붙은 지난 시각은 409가 아니라 400으로 거절한다")
    void invalidOptionWinsOverOutOfRange() {
        // given -> 값 자체가 틀린 요청은 시각 범위보다 먼저 걸러야 클라가 원인을 안다
        OpenInviteRoomCommand command = new OpenInviteRoomCommand(
                userId, LocalDateTime.now().minusDays(1).withSecond(30), TARGET_DISTANCE);

        // when & then
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(InvalidInviteOptionException.class);
    }

    @Test
    @DisplayName("고를 수 없는 시각이면 409로 거절하고 DB를 보지 않는다")
    void rejectsOutOfRangeStartAt() {
        // given -> 내일은 언제든 범위 밖이다
        LocalDateTime tomorrow = LocalDateTime.now().toLocalDate().plusDays(1).atTime(7, 30);

        // when & then
        assertThatThrownBy(() -> handler.handle(commandAt(userId, tomorrow)))
                .isInstanceOf(InviteStartAtOutOfRangeException.class);

        verifyNoInteractions(existsActiveApplicationPort, loadUserAvgPacePort,
                createMatchApplicationPort, createMatchRoomPort, scheduleJobPort);
    }

    @Test
    @DisplayName("활성 신청이 있으면 초대 방을 열지 못한다")
    void rejectsWhenAlreadyRunning() {
        // given -> 매칭 대기·다른 초대 방·러닝 중 모두 여기 걸린다
        givenSelectable();
        given(existsActiveApplicationPort.existsActive(new UserId(userId))).willReturn(true);

        // when & then
        assertThatThrownBy(() -> handler.handle(commandAt(userId, START)))
                .isInstanceOf(AlreadyRunningException.class);

        verifyNoInteractions(loadUserAvgPacePort, createMatchApplicationPort, createMatchRoomPort, scheduleJobPort);
    }

    @Test
    @DisplayName("온보딩을 하지 않았으면 초대 방을 열지 못한다")
    void rejectsWhenOnboardingNotCompleted() {
        // given
        givenSelectable();
        given(existsActiveApplicationPort.existsActive(new UserId(userId))).willReturn(false);
        given(loadUserAvgPacePort.loadAvgPace(new UserId(userId))).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> handler.handle(commandAt(userId, START)))
                .isInstanceOf(OnboardingNotCompletedException.class);

        // 신청만 남으면 활성 신청에 걸려 유저가 영영 다시 못 뛴다
        verifyNoInteractions(createMatchApplicationPort, createMatchRoomPort, scheduleJobPort);
    }
}
