package com.runiverse.running_service.unit_test.running.application;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.match.common.MatchProperties;
import com.runiverse.running_service.application.running.command.finish.FinishRunningCommand;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunningRoomCommand;
import com.runiverse.running_service.application.running.command.forcefinish.ForceFinishRunningRoomHandler;
import com.runiverse.running_service.application.running.port.in.FinishRunningUsecase;
import com.runiverse.running_service.application.running.port.out.ExistsRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRoomPlayerPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.LockRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.LockRunningRoomPort;
import com.runiverse.running_service.application.running.port.out.StartMatchCooldownPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningPlayerPort;
import com.runiverse.running_service.application.running.port.out.UpdateRunningRoomPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerStatus;
import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.SessionDraft;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomStatus;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomType;
import com.runiverse.running_service.domain.running.player.vo.RunningPlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 강제 종료 단위 테스트")
class ForceFinishRunningRoomHandlerTest {

    private static final long ROOM_ID = 125L;
    private static final int AVG_PACE = 360;
    private static final int TARGET_DISTANCE = 5_000;
    private static final Duration MATCH_COOLDOWN = Duration.ofMinutes(20);
    // 강제 종료는 예약이 시각을 이미 판단했다 — 핸들러는 오프셋을 보지 않는다
    private static final MatchProperties PROPERTIES = new MatchProperties(
            Duration.ofMinutes(10), Duration.ofSeconds(10), Duration.ofHours(6),
            10, MATCH_COOLDOWN);

    private static final UserId RUNNER = new UserId(UuidCreator.getTimeOrderedEpoch());
    private static final UserId NO_SHOW = new UserId(UuidCreator.getTimeOrderedEpoch());

    @Mock
    private LockRunningPlayerPort lockRunningPlayerPort;

    @Mock
    private LockRunningRoomPort lockRunningRoomPort;

    @Mock
    private LoadRunningRoomPort loadRunningRoomPort;

    @Mock
    private LoadRoomPlayerPort loadRoomPlayerPort;

    @Mock
    private UpdateRunningRoomPort updateRunningRoomPort;

    @Mock
    private UpdateRunningPlayerPort updateRunningPlayerPort;

    @Mock
    private StartMatchCooldownPort startMatchCooldownPort;

    @Mock
    private ExistsRunningRecordPort existsRunningRecordPort;

    @Mock
    private FinishRunningUsecase finishRunningUsecase;

    private ForceFinishRunningRoomHandler forceFinishRunningRoomHandler;

    @BeforeEach
    void setUp() {
        forceFinishRunningRoomHandler = new ForceFinishRunningRoomHandler(
                lockRunningPlayerPort, lockRunningRoomPort, loadRunningRoomPort, loadRoomPlayerPort,
                updateRunningRoomPort, updateRunningPlayerPort, startMatchCooldownPort,
                existsRunningRecordPort, finishRunningUsecase, PROPERTIES);
    }

    @Test
    @DisplayName("뛰던 참가자는 평소 러닝 종료 경로로 넘긴다")
    void delegatesRunnerToFinishRunning() {
        // given -> 트랙 분석·기록 생성·거리 비율 판정을 여기서 다시 만들지 않는다
        givenLockedRoom(room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);
        givenReloadedRoom(finishedRoom(RUNNER, NO_SHOW));

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 강제 종료라는 사실만 넘기고 최종 상태는 그쪽이 정한다
        verify(finishRunningUsecase).handle(
                new FinishRunningCommand(ROOM_ID, RUNNER.value(), true));
    }

    @Test
    @DisplayName("참가자를 방보다 먼저 잠근다")
    void locksPlayersBeforeRoom() {
        // given -> 시작·취소·탈퇴 정산이 모두 참가자 → 방 순이라, 반대로 잡으면 교착에 빠진다
        givenLockedRoom(room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);
        givenReloadedRoom(finishedRoom(RUNNER, NO_SHOW));

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        InOrder inOrder = inOrder(lockRunningPlayerPort, lockRunningRoomPort);
        inOrder.verify(lockRunningPlayerPort).lockActiveInRoom(new RunningRoomId(ROOM_ID));
        inOrder.verify(lockRunningRoomPort).lockById(new RunningRoomId(ROOM_ID));
    }

    @Test
    @DisplayName("한 번도 붙지 않은 참가자는 확정 후 이탈로 닫는다")
    void closesNoShowAsMatchedLeft() {
        // given -> 2인 확정 방이라 안 나타난 것이 곧 상대를 곤란하게 만든 것이다
        givenLockedRoom(room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);
        givenReloadedRoom(finishedRoom(RUNNER, NO_SHOW));

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> RUNNING을 거치지 않았으므로 조기 종료가 아니라 확정 후 이탈이다
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        // 상태가 MATCHED_LEFT_*라 쿨다운도 조기 종료가 아니라 이탈 쪽 값을 쓴다
        verify(startMatchCooldownPort).start(NO_SHOW, MATCH_COOLDOWN);
    }

    @Test
    @DisplayName("1인 확정 방의 미출석은 제재하지 않는다")
    void doesNotPenalizeNoShowInSinglePlayerRoom() {
        // given -> 안 나타나도 곤란해지는 상대가 없다. 시작 전 이탈 면제와 같은 기준이다
        givenLockedRoom(room(RunningRoomStatus.STARTED, NO_SHOW));
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);
        givenReloadedRoom(room(RunningRoomStatus.STARTED, NO_SHOW));

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        verifyNoInteractions(startMatchCooldownPort);
    }

    @Test
    @DisplayName("판정 기준은 순회 전에 굳혀 전원에게 같게 적용한다")
    void freezesPenaltyBaseBeforeLoop() {
        // given -> 2인 방에서 둘 다 안 나타났다. 처리하면서 인원을 세면
        //          뒤에 처리된 사람만 1인 방으로 보여 면제받는다
        givenLockedRoom(room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.JOINED);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);
        givenReloadedRoom(room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 둘 다 제재 대상이다
        assertThat(updatedPlayers())
                .extracting(RunningPlayer::getStatus)
                .containsOnly(RunningPlayerStatus.MATCHED_LEFT_PENALTY);
        verify(startMatchCooldownPort).start(RUNNER, MATCH_COOLDOWN);
        verify(startMatchCooldownPort).start(NO_SHOW, MATCH_COOLDOWN);
    }

    @Test
    @DisplayName("아무도 뛰지 않은 방은 남길 기록이 없어 취소로 닫는다")
    void cancelsRoomNobodyRanIn() {
        // given -> 전원이 채널에 붙지 않았다. 방을 닫아 줄 러닝 종료가 하나도 없다
        givenLockedRoom(room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));
        givenPlayer(RUNNER, RunningPlayerStatus.JOINED);
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);
        givenReloadedRoom(room(RunningRoomStatus.STARTED, RUNNER, NO_SHOW));

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        assertThat(lastUpdatedRoom().getStatus()).isEqualTo(RunningRoomStatus.CANCELLED);
        assertThat(lastUpdatedRoom().getCloseAt()).isPresent();
        verifyNoInteractions(finishRunningUsecase);
    }

    @Test
    @DisplayName("러닝 종료가 이미 닫은 방은 다시 닫지 않는다")
    void leavesRoomClosedByFinishRunning() {
        // given -> 마지막 참가자가 끝나는 순간 러닝 종료 안에서 방이 FINISHED가 된다.
        //          여기서 또 닫으면 그 전이를 덮어써 기록 있는 방이 취소로 남는다
        givenLockedRoom(room(RunningRoomStatus.STARTED, RUNNER));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        givenReloadedRoom(finishedRoom(RUNNER));

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 다시 읽어 이미 닫힌 것을 보고 손을 뗀다. 기록을 세어볼 것도 없다
        assertThat(lastUpdatedRoom().getStatus()).isEqualTo(RunningRoomStatus.STARTED);
        verifyNoInteractions(existsRunningRecordPort);
    }

    @Test
    @DisplayName("러닝 종료가 방을 닫지 못했으면 기록 유무로 닫는다")
    void closesRoomLeftOpenByRecordExistence() {
        // given -> 뛴 사람은 확정됐는데 방이 열린 채로 남았다.
        //          남길 기록이 있으니 취소가 아니라 완료로 닫아야 결과 조회가 이어진다
        givenLockedRoom(room(RunningRoomStatus.STARTED, RUNNER));
        givenPlayer(RUNNER, RunningPlayerStatus.RUNNING);
        givenReloadedRoom(room(RunningRoomStatus.STARTED, RUNNER));
        given(existsRunningRecordPort.existsInRoom(new RunningRoomId(ROOM_ID)))
                .willReturn(true);

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        assertThat(lastUpdatedRoom().getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
        assertThat(lastUpdatedRoom().getCloseAt()).isPresent();
    }

    @Test
    @DisplayName("이미 닫힌 방에 남은 미출석자도 끝까지 닫는다")
    void closesNoShowLeftBehindInClosedRoom() {
        // given -> 뛴 사람이 정상 종료해 방은 이미 FINISHED다. 그런데 안 나타난 사람은
        //          RUNNING이 아니라 종료 판정에 세어지지 않아 활성 신청으로 남는다.
        //          여기서 닫지 않으면 그 사람은 다음 러닝을 영영 못 한다
        givenLockedRoom(finishedRoom(NO_SHOW));
        givenPlayer(NO_SHOW, RunningPlayerStatus.JOINED);
        givenReloadedRoom(finishedRoom(NO_SHOW));

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then -> 신청은 닫히고, 이미 종료된 방은 그대로 둔다
        assertThat(updatedPlayer().getStatus())
                .isEqualTo(RunningPlayerStatus.MATCHED_LEFT_NO_PENALTY);
        assertThat(lastUpdatedRoom().getStatus()).isEqualTo(RunningRoomStatus.FINISHED);
    }

    @Test
    @DisplayName("방이 없으면 아무것도 하지 않는다")
    void skipsWhenRoomIsGone() {
        // given -> 예약만 남고 방이 사라진 경우. 예약을 소비한 것으로 보고 끝낸다
        given(lockRunningRoomPort.lockById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.empty());

        // when
        forceFinishRunningRoomHandler.handle(new ForceFinishRunningRoomCommand(ROOM_ID));

        // then
        verifyNoInteractions(updateRunningRoomPort, updateRunningPlayerPort,
                startMatchCooldownPort, finishRunningUsecase);
    }

    private void givenLockedRoom(RunningRoom room) {
        given(lockRunningRoomPort.lockById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.of(room));
    }

    // 러닝 종료가 방을 다시 읽어 고쳤을 수 있어 마지막 정리는 새로 읽는다 —
    // 그래서 잠글 때와 다른 인스턴스를 돌려준다
    private void givenReloadedRoom(RunningRoom room) {
        given(loadRunningRoomPort.loadById(new RunningRoomId(ROOM_ID)))
                .willReturn(Optional.of(room));
    }

    private void givenPlayer(UserId userId, RunningPlayerStatus status) {
        given(loadRoomPlayerPort.load(new RunningRoomId(ROOM_ID), userId))
                .willReturn(Optional.of(player(userId, status)));
    }

    private RunningPlayer updatedPlayer() {
        List<RunningPlayer> updated = updatedPlayers();
        assertThat(updated).hasSize(1);
        return updated.get(0);
    }

    private List<RunningPlayer> updatedPlayers() {
        ArgumentCaptor<RunningPlayer> captor = ArgumentCaptor.forClass(RunningPlayer.class);
        verify(updateRunningPlayerPort, atLeastOnce()).update(captor.capture());
        return captor.getAllValues();
    }

    private RunningRoom lastUpdatedRoom() {
        ArgumentCaptor<RunningRoom> captor = ArgumentCaptor.forClass(RunningRoom.class);
        verify(updateRunningRoomPort, atLeastOnce()).update(captor.capture());
        return captor.getValue();
    }

    private static RunningPlayer player(UserId userId, RunningPlayerStatus status) {
        return RunningPlayer.builder()
                .runningPlayerId(7L)
                .userId(userId.value())
                .status(status)
                .avgPace(AVG_PACE)
                .targetDistance(TARGET_DISTANCE)
                .startAt(LocalDateTime.now().minusHours(6))
                .build();
    }

    private static RunningRoom room(RunningRoomStatus status, UserId... members) {
        return buildRoom(status, null, members);
    }

    // 뛴 사람이 정상 종료해 이미 닫힌 방 — 미출석자의 세션만 아직 붙어 있다
    private static RunningRoom finishedRoom(UserId... members) {
        return buildRoom(RunningRoomStatus.FINISHED, LocalDateTime.now(), members);
    }

    private static RunningRoom buildRoom(RunningRoomStatus status, LocalDateTime closeAt,
                                         UserId... members) {
        List<SessionDraft> sessions = new ArrayList<>();
        long playerId = 7L;
        for (UserId member : members) {
            sessions.add(new SessionDraft(member, new RunningPlayerId(playerId++), 0, true));
        }
        return RunningRoom.builder()
                .runningRoomId(ROOM_ID)
                .type(RunningRoomType.MATCH)
                .status(status)
                // 아무도 취소·탈퇴하지 않은 방이라 확정 인원 그대로다 — 제재 판정이 이 값을 본다
                .currentPlayerCount(members.length)
                .maxPlayerCount(4)
                .startAt(LocalDateTime.now().minusHours(6))
                .targetDistance(TARGET_DISTANCE)
                .avgPace(AVG_PACE)
                .sessions(sessions)
                .closeAt(closeAt)
                .build();
    }
}
