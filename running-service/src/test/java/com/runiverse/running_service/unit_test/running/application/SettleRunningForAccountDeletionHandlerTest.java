package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.command.stream.MatchStreamCloseRequestedEvent;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.match.common.MatchRoomChangedEvent;
import com.runiverse.running_service.application.match.common.RoomInfoAssembler;
import com.runiverse.running_service.application.match.port.out.LoadMatchRoomPort;
import com.runiverse.running_service.application.match.port.out.LockMatchApplicationPort;
import com.runiverse.running_service.application.match.port.out.MatchStreamEvent;
import com.runiverse.running_service.application.match.port.out.RoomInfo;
import com.runiverse.running_service.application.match.port.out.UpdateMatchRoomPort;
import com.runiverse.running_service.application.running.command.accountdeletion.SettleRunningForAccountDeletionCommand;
import com.runiverse.running_service.application.running.command.accountdeletion.SettleRunningForAccountDeletionHandler;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.session.RunningConnectionCloseRequestedEvent;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.StartMatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원탈퇴 러닝 정리 단위 테스트")
class SettleRunningForAccountDeletionHandlerTest {

    private static final UUID USER_ID = UuidCreator.getTimeOrderedEpoch();
    private static final long PLAYER_ID = 7L;
    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5_000;
    private static final Duration MATCH_COOLDOWN = Duration.ofMinutes(20);
    // 탈퇴는 오프셋을 보지 않는다 — 쿨다운 길이만 쓴다
    private static final MatchProperties MATCH_PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofHours(6),
            10, MATCH_COOLDOWN);
    // 조립 결과는 이 테스트의 주제가 아니다 — 발행 여부만 본다
    private static final RoomInfo ROOM_INFO = new RoomInfo(
            ROOM_ID, RunningRoomStatus.MATCHING, LocalDateTime.now().plusHours(2),
            LocalDateTime.now().plusHours(1), TARGET_DISTANCE, AVG_PACE, List.of());

    @Mock
    private LockMatchApplicationPort lockMatchApplicationPort;
    @Mock
    private LoadMatchRoomPort loadMatchRoomPort;
    @Mock
    private LockRunningRoomPort lockRunningRoomPort;
    @Mock
    private UpdateMatchRoomPort updateMatchRoomPort;
    @Mock
    private UpdateRunningPlayerPort updateRunningPlayerPort;
    @Mock
    private StartMatchCooldownPort startMatchCooldownPort;
    @Mock
    private FinishRunningUsecase finishRunningUsecase;
    @Mock
    private RoomInfoAssembler roomInfoAssembler;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SettleRunningForAccountDeletionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SettleRunningForAccountDeletionHandler(
                lockMatchApplicationPort, loadMatchRoomPort, lockRunningRoomPort,
                updateMatchRoomPort, updateRunningPlayerPort,
                startMatchCooldownPort, finishRunningUsecase, roomInfoAssembler,
                MATCH_PROPERTIES, eventPublisher);
    }

    @Test
    @DisplayName("활성 신청이 없어도 매칭 스트림 종료는 요청한다")
    void requestsStreamCloseEvenWithoutActiveApplication() {
        // given -> 탈퇴자 대부분이 이 경우다
        given(lockMatchApplicationPort.lockActive(new UserId(USER_ID)))
                .willReturn(Optional.empty());

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then -> 연결은 커밋 뒤에 닫는다. 여기서 닫으면 롤백돼도 되살릴 수 없다
        verify(eventPublisher).publishEvent(new MatchStreamCloseRequestedEvent(new UserId(USER_ID)));
        verify(eventPublisher).publishEvent(new RunningConnectionCloseRequestedEvent(new UserId(USER_ID)));
        verifyNoInteractions(loadMatchRoomPort, lockRunningRoomPort, finishRunningUsecase);
    }

    @Test
    @DisplayName("시작 전 방이면 신청을 지우지 않고 일반 취소처럼 닫는다")
    void closesApplicationLikeCancelWhenRoomNotStarted() {
        // given -> 모집 중인 방. 신청 이력은 통계로 남긴다(erd)
        givenActiveApplication(room(RunningRoomStatus.MATCHING, 2));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then -> 마감 전이라 대기 취소다
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        assertThat(updatedPlayer().getDeletedAt()).isPresent();
        verifyNoInteractions(startMatchCooldownPort, finishRunningUsecase);
    }

    @Test
    @DisplayName("마감이 지난 2인 이상 방이면 제재 대상으로 남기되 쿨다운은 걸지 않는다")
    void recordsPenaltyWithoutCooldownAfterClose() {
        // given -> 시작 5분 전이라 마감(10분 전)이 지났다. 일반 취소라면 제재 대상이다
        givenActiveApplication(room(RunningRoomStatus.MATCHED, 2,
                LocalDateTime.now().plusMinutes(5)));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then -> 사유는 취소와 같게 남기고, 막을 다음 신청이 없으니 쿨다운은 걸지 않는다
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        verifyNoInteractions(startMatchCooldownPort);
    }

    @Test
    @DisplayName("확정된 방에서 나가면 인원이 줄고 방은 유지된다")
    void leavesMatchedRoomAndKeepsIt() {
        // given -> 남은 사람이 있으면 방을 닫지 않는다
        givenActiveApplication(room(RunningRoomStatus.MATCHED, 2));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        RunningRoom updated = updatedRoom();
        assertThat(updated.getPlayerCount().current()).isOne();
        assertThat(updated.getStatus()).isEqualTo(RunningRoomStatus.MATCHED);
    }

    @Test
    @DisplayName("혼자였던 방은 인원이 0이 되어 취소된다")
    void cancelsRoomWhenLastPlayerLeaves() {
        // given
        givenActiveApplication(room(RunningRoomStatus.MATCHING, 1));

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        RunningRoom updated = updatedRoom();
        assertThat(updated.getPlayerCount().current()).isZero();
        assertThat(updated.getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
    }

    @Test
    @DisplayName("인원이 0이면 남은 참가자가 없어 이벤트를 발행하지 않는다")
    void skipsEventWhenRoomBecomesEmpty() {
        // given
        givenActiveApplication(room(RunningRoomStatus.MATCHING, 1));

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then -> 연결 종료 요청만 나간다
        verify(eventPublisher).publishEvent(new MatchStreamCloseRequestedEvent(new UserId(USER_ID)));
        verify(eventPublisher, never()).publishEvent(any(MatchRoomChangedEvent.class));
    }

    @Test
    @DisplayName("남은 참가자에게 방 갱신을 발행한다")
    void publishesRoomUpdateToRemainingPlayers() {
        // given
        givenActiveApplication(room(RunningRoomStatus.MATCHING, 2));
        given(roomInfoAssembler.assemble(any(RunningRoom.class))).willReturn(ROOM_INFO);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        verify(eventPublisher).publishEvent(new MatchRoomChangedEvent(
                MatchStreamEvent.updated(ROOM_INFO)));
    }

    @Test
    @DisplayName("뛰던 참가자는 종료 경로로 보내고 신청을 남긴다")
    void finishesRunningWhenPlayerWasRunning() {
        // given
        givenActiveApplication(room(RunningRoomStatus.STARTED, 2), RunningPlayerStatus.RUNNING);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        verify(finishRunningUsecase).handle(new FinishRunningCommand(ROOM_ID, USER_ID, true));
        verify(updateMatchRoomPort, never()).update(any());
        verifyNoInteractions(updateRunningPlayerPort);
    }

    @Test
    @DisplayName("시작된 방의 미출석자는 확정 후 이탈로 닫는다")
    void closesNoShowAsMatchedLeft() {
        // given -> 시작 시각에 앱을 켜지 않으면 방만 STARTED가 되고 참가자는 JOINED로 남는다.
        //          종료 경로로 보내면 확정할 러닝이 없어 거절당한다
        givenActiveApplication(room(RunningRoomStatus.STARTED, 2), RunningPlayerStatus.JOINED);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        assertThat(updatedPlayer().getDeletedAt()).isPresent();
        verify(startMatchCooldownPort).start(new UserId(USER_ID), MATCH_COOLDOWN);
        verifyNoInteractions(finishRunningUsecase);
        // 인원은 그대로다
        assertThat(updatedRoom().getPlayerCount().current()).isEqualTo(2);
        assertThat(updatedRoom().getStatus()).isEqualTo(RunningRoomStatus.STARTED);
    }

    @Test
    @DisplayName("1인 확정 방의 미출석은 제재하지 않는다")
    void doesNotPenalizeNoShowInSinglePlayerRoom() {
        // given -> 안 나타나도 곤란해지는 상대가 없다
        givenActiveApplication(room(RunningRoomStatus.STARTED, 1), RunningPlayerStatus.JOINED);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        verifyNoInteractions(startMatchCooldownPort);
    }

    @ParameterizedTest
    @EnumSource(value = RunningRoomStatus.class, names = {"FINISHED", "CANCELLED"})
    @DisplayName("이미 닫힌 방에 남은 미출석자도 같은 규칙으로 닫는다")
    void closesNoShowLeftBehindInClosedRoom(RunningRoomStatus status) {
        // given -> 뛰던 사람이 전원 끝내면 방은 그 시점에 닫히고 미출석자만 JOINED로 남는다.
        //          남길 기록이 있었으면 FINISHED, 없었으면 CANCELLED다
        givenActiveApplication(room(status, 2), RunningPlayerStatus.JOINED);

        // when
        handler.handle(new SettleRunningForAccountDeletionCommand(USER_ID));

        // then
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        verifyNoInteractions(finishRunningUsecase);
    }

    private void givenActiveApplication(RunningRoom room) {
        givenActiveApplication(room, RunningPlayerStatus.JOINED);
    }

    private void givenActiveApplication(RunningRoom room, RunningPlayerStatus status) {
        given(lockMatchApplicationPort.lockActive(new UserId(USER_ID)))
                .willReturn(Optional.of(player(status)));
        given(loadMatchRoomPort.findAssignedRoom(new UserId(USER_ID)))
                .willReturn(Optional.of(new RunningRoomId(ROOM_ID)));
        given(lockRunningRoomPort.lockById(new RunningRoomId(ROOM_ID))).willReturn(Optional.of(room));
    }

    private RunningRoom updatedRoom() {
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateMatchRoomPort).update(captor.capture());
        return captor.getValue();
    }

    private RunningPlayer updatedPlayer() {
        ArgumentCaptor<RunningPlayer> captor = ArgumentCaptor.forClass(RunningPlayer.class);
        verify(updateRunningPlayerPort, atLeastOnce()).update(captor.capture());
        return captor.getValue();
    }

    private static RunningPlayer player(RunningPlayerStatus status) {
        return RunningPlayer.builder()
                .runningPlayerId(PLAYER_ID)
                .userId(USER_ID)
                .status(status)
                .avgPace(AVG_PACE)
                .targetDistance(TARGET_DISTANCE)
                .startAt(LocalDateTime.now().plusHours(2))
                .build();
    }

    private static RunningRoom room(RunningRoomStatus status, int currentPlayerCount) {
        return room(status, currentPlayerCount, LocalDateTime.now().plus(Duration.ofHours(2)));
    }

    // 나 말고 나머지 인원은 다른 유저의 세션으로 채운다 — 세션 키가 유저다
    private static RunningRoom room(RunningRoomStatus status, int currentPlayerCount,
                                    LocalDateTime startAt) {
        List<SessionDraft> sessions = new ArrayList<>();
        sessions.add(new SessionDraft(
                new UserId(USER_ID), new RunningPlayerId(PLAYER_ID), 0, true));
        for (int i = 1; i < currentPlayerCount; i++) {
            sessions.add(new SessionDraft(
                    new UserId(UuidCreator.getTimeOrderedEpoch()),
                    new RunningPlayerId(PLAYER_ID + i), 0, true));
        }
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(status)
                .startAt(startAt)
                // 닫힌 시각은 종료 상태와 짝이라 어긋나면 복원이 막힌다
                .closeAt(status.isTerminal() ? LocalDateTime.now() : null)
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .currentPlayerCount(currentPlayerCount)
                .maxPlayerCount(4)
                .sessions(sessions)
                .build();
    }
}
