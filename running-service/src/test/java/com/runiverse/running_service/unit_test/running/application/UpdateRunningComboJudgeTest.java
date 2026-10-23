package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.combo.RunningComboProperties;
import com.runiverse.running_service.application.running.command.combo.RunningComboSignal;
import com.runiverse.running_service.application.running.command.combo.UpdateRunningComboJudge;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.MarkRunningComboPausedPort;
import com.runiverse.running_service.application.running.port.out.PublishRunningComboPort;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboSegment;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSegmentsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSnapshotPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import ch.qos.logback.classic.Level;
import com.runiverse.running_service.support.LogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 판정 규칙은 RunningComboEvaluator가 맡고, 여기서는 그 앞뒤 — 속도·상태 재료를 이어 쓰는 방법,
// 저장 순서, 실패를 삼키는 계약을 본다. 안에서 Instant.now()를 부르므로 경과 시간은 허용 오차로 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 콤보 판정 조립 단위 테스트")
class UpdateRunningComboJudgeTest {

    private static final Long ROOM_ID = 42L;
    private static final UserId SENDER =
            new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000a1"));
    private static final UserId PEER =
            new UserId(UUID.fromString("0190a5b4-3c2d-7e1f-8a2b-0000000000b2"));
    // 실제 application.properties와 같은 값으로 맞춘다
    private static final RunningComboProperties PROPERTIES = new RunningComboProperties(
            30, 30, Duration.ofSeconds(19), Duration.ofSeconds(10), 1);
    // 관측했고 의심점도 옮겨진 배치 — 달리는 중
    private static final RunningComboSignal RUNNING_AT_1000 = new RunningComboSignal(1_000, true, true, false);

    @Mock
    private LoadRunningComboSnapshotsPort loadRunningComboSnapshotsPort;

    @Mock
    private SaveRunningComboSnapshotPort saveRunningComboSnapshotPort;

    @Mock
    private LoadRunningComboPairsPort loadRunningComboPairsPort;

    @Mock
    private SaveRunningComboPairsPort saveRunningComboPairsPort;

    @Mock
    private SaveRunningComboSegmentsPort saveRunningComboSegmentsPort;

    @Mock
    private MarkRunningComboPausedPort markRunningComboPausedPort;

    @Mock
    private PublishRunningComboPort publishRunningComboPort;

    private UpdateRunningComboJudge judge;

    @BeforeEach
    void setUp() {
        judge = new UpdateRunningComboJudge(
                loadRunningComboSnapshotsPort,
                saveRunningComboSnapshotPort,
                loadRunningComboPairsPort,
                saveRunningComboPairsPort,
                saveRunningComboSegmentsPort,
                markRunningComboPausedPort,
                publishRunningComboPort,
                PROPERTIES);
    }

    @Test
    @DisplayName("첫 배치는 직전 값이 없어 속도가 0이다")
    void judge_savesZeroSpeedOnFirstBatch() {
        // given -> 저장된 스냅샷이 없다

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then -> 방금 보고한 값이라 보정으로 밀 것도 없다
        RunningComboSnapshot saved = captureSavedSnapshot();
        assertThat(saved.userId()).isEqualTo(SENDER);
        assertThat(saved.meters()).isEqualTo(1_000);
        assertThat(saved.speedMetersPerSecond()).isZero();
    }

    @Test
    @DisplayName("직전 스냅샷이 있으면 거리 증가분을 경과 시간으로 나눠 속도를 구한다")
    void judge_derivesSpeedFromPreviousSnapshot() {
        // given -> 100초 전에 900m였고 지금 1000m다
        givenSenderSnapshot(900, Instant.now().minusSeconds(100), 0, false);

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then -> 100m / 100초 = 1.0m/s. Instant.now() 호출 간격만큼 오차가 있다
        assertThat(captureSavedSnapshot().speedMetersPerSecond()).isCloseTo(1.0, within(0.05));
    }

    @Test
    @DisplayName("누적 거리가 줄어 보여도 속도는 음수가 되지 않는다")
    void judge_clampsNegativeSpeedToZero() {
        // given -> 저장이 밀려 옛 값을 읽으면 증가분이 음수로 나온다.
        // 음수 속도는 보정을 뒤로 밀어 멀쩡한 콤보를 끊는다
        givenSenderSnapshot(2_000, Instant.now().minusSeconds(10), 5, false);

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then
        assertThat(captureSavedSnapshot().speedMetersPerSecond()).isZero();
    }

    @Test
    @DisplayName("직전 기준 시각이 지금보다 뒤면 직전 속도를 이어 쓴다")
    void judge_keepsPreviousSpeedWhenElapsedIsNotPositive() {
        // given -> 인스턴스 간 시계가 어긋나면 직전 기준 시각이 미래일 수 있다.
        // 경과가 0 이하면 나눌 수 없다
        givenSenderSnapshot(900, Instant.now().plusSeconds(10), 3.5, false);

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then
        assertThat(captureSavedSnapshot().speedMetersPerSecond()).isEqualTo(3.5);
    }

    @Test
    @DisplayName("관측·이동이 있으면 그 시각을 지금으로 남긴다")
    void judge_stampsObservationAndMovement() {
        // given
        Instant before = Instant.now();

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then
        RunningComboSnapshot saved = captureSavedSnapshot();
        assertThat(saved.observedAt()).isAfterOrEqualTo(before);
        assertThat(saved.movedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("관측·이동이 없던 배치는 직전 시각을 이어 쓴다 — 그래야 서 있음·관측 없음으로 늙어 간다")
    void judge_carriesPreviousTimesWhenNothingChanged() {
        // given -> 직전 배치에서 관측·이동한 시각
        Instant movedAt = Instant.now().minusSeconds(12);
        Instant observedAt = Instant.now().minusSeconds(8);
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(SENDER, 900, observedAt, 0, movedAt, observedAt, false, false)));

        // when -> 재전송분만 온 배치
        judge.judge(ROOM_ID, SENDER, new RunningComboSignal(900, false, false, false));

        // then
        RunningComboSnapshot saved = captureSavedSnapshot();
        assertThat(saved.movedAt()).isEqualTo(movedAt);
        assertThat(saved.observedAt()).isEqualTo(observedAt);
    }

    @Test
    @DisplayName("첫 기준(RUNNING_START)은 관측·이동 시각을 비워 둔다")
    void judge_leavesTimesEmptyOnBaseline() {
        // when
        judge.judge(ROOM_ID, SENDER, RunningComboSignal.baseline(0));

        // then -> 출발선에서는 콤보가 붙지 않는다
        RunningComboSnapshot saved = captureSavedSnapshot();
        assertThat(saved.movedAt()).isNull();
        assertThat(saved.observedAt()).isNull();
    }

    @Test
    @DisplayName("일시정지였던 참가자의 관측이 다시 들어오면 일시정지 표시를 지운다")
    void judge_clearsPauseWhenObservedAgain() {
        // given
        givenSenderSnapshot(900, Instant.now().minusSeconds(30), 0, true);

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then -> 다시 달리는 중이 되는 것은 좌표로만이다
        assertThat(captureSavedSnapshot().paused()).isFalse();
        verify(markRunningComboPausedPort).clearPaused(ROOM_ID, SENDER);
    }

    @Test
    @DisplayName("일시정지 중 관측 없는 배치가 오면 표시를 그대로 둔다")
    void judge_keepsPauseWithoutObservation() {
        // given
        givenSenderSnapshot(900, Instant.now().minusSeconds(30), 0, true);

        // when -> 재전송분만 왔다
        judge.judge(ROOM_ID, SENDER, new RunningComboSignal(900, false, false, false));

        // then
        assertThat(captureSavedSnapshot().paused()).isTrue();
        verify(markRunningComboPausedPort, never()).clearPaused(anyLong(), any());
    }

    @Test
    @DisplayName("일시정지 표시가 없으면 지우지 않는다 — 배치마다 쓰기를 늘리지 않는다")
    void judge_doesNotClearWhenNotPaused() {
        // given
        givenSenderSnapshot(900, Instant.now().minusSeconds(10), 0, false);

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then
        verify(markRunningComboPausedPort, never()).clearPaused(anyLong(), any());
    }

    @Test
    @DisplayName("끝난 참가자의 늦은 배치가 들어와도 종료 표시는 그대로다")
    void judge_keepsFinishedWhenObservedAgain() {
        // given
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(SENDER, 900, Instant.now().minusSeconds(30), 0,
                        Instant.now().minusSeconds(30), Instant.now().minusSeconds(30), false, true)));

        // when -> 종료 직후 상태 조회가 실패한 틈에 늦은 좌표가 통과했다
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then -> 끝난 사람은 다시 달리는 중이 될 수 없다
        assertThat(captureSavedSnapshot().finished()).isTrue();
    }

    @Test
    @DisplayName("판정은 방금 저장한 값으로 돈다")
    void judge_evaluatesWithTheUpdatedSnapshot() {
        // given -> 저장된 목록에 보낸 사람이 없어도 이번 배치 값으로 비교해야 한다
        givenRunningPeer(1_020);

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then -> 20m 차이라 콤보가 붙고 둘 다 받는다
        RunningComboUpdate update = captureUpdate();
        assertThat(update.relations()).hasSize(1);
        assertThat(update.relations().getFirst().gapMeters()).isEqualTo(20);
        assertThat(update.recipients()).containsExactlyInAnyOrder(SENDER.value(), PEER.value());
    }

    @Test
    @DisplayName("구간 저장 → 관계 저장 → 발행 순서다")
    void judge_savesSegmentsThenPairsThenPublishes() {
        // given
        givenRunningPeer(1_020);

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then -> 구간보다 관계가 먼저 저장되면 구간 저장 실패 때 그 구간은 되살릴 수 없다.
        // 관계보다 발행이 먼저면 화면에 띄운 콤보를 서버가 모른다
        InOrder order = inOrder(saveRunningComboSegmentsPort, saveRunningComboPairsPort, publishRunningComboPort);
        order.verify(saveRunningComboSegmentsPort).saveSegments(anyLong(), anyList());
        order.verify(saveRunningComboPairsPort).savePairs(anyLong(), any());
        order.verify(publishRunningComboPort).publish(anyLong(), any());
    }

    @Test
    @DisplayName("끊긴 구간을 구간 저장에 넘긴다")
    void judge_savesClosedSegment() {
        // given -> 봐주기가 다 된 콤보가 50m 벌어진다
        givenRunningPeer(1_050);
        Instant startedAt = Instant.now().minusSeconds(60);
        Instant accruedAt = Instant.now().minusSeconds(20);
        given(loadRunningComboPairsPort.loadPairs(ROOM_ID)).willReturn(List.of(
                new RunningComboPair(SENDER, PEER, startedAt, accruedAt, 4, 0)));

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then
        ArgumentCaptor<List<RunningComboSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(saveRunningComboSegmentsPort).saveSegments(eq(ROOM_ID), captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new RunningComboSegment(SENDER, PEER, startedAt, accruedAt, 4));
    }

    @Test
    @DisplayName("구간 저장이 실패하면 관계를 저장하지 않고 발행도 하지 않는다")
    void judge_skipsPairsWhenSegmentSaveFails() {
        // given
        givenRunningPeer(1_020);
        willThrow(new IllegalStateException("redis down"))
                .given(saveRunningComboSegmentsPort).saveSegments(anyLong(), anyList());

        // when & then -> 관계가 이어진 채로 남아야 다음 배치가 같은 구간을 다시 닫는다
        assertThatCode(() -> judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000)).doesNotThrowAnyException();
        verify(saveRunningComboPairsPort, never()).savePairs(anyLong(), any());
        verify(publishRunningComboPort, never()).publish(anyLong(), any());
    }

    @Test
    @DisplayName("읽기가 실패해도 밖으로 던지지 않는다")
    void judge_swallowsLoadFailure() {
        // given -> 좌표는 이미 저장됐고 진행 통지도 나간 뒤라
        // 화면 표시 하나 때문에 클라가 ERROR를 받으면 안 된다
        willThrow(new IllegalStateException("redis down"))
                .given(loadRunningComboSnapshotsPort).loadSnapshots(ROOM_ID);

        // when & then
        assertThatCode(() -> judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000)).doesNotThrowAnyException();
        // 판정을 못 했으므로 아무것도 내보내지 않는다 — 빈 통을 보내면 화면의 콤보가 지워진다
        verify(publishRunningComboPort, never()).publish(anyLong(), any());
    }

    @Test
    @DisplayName("판정이 실패하면 userId를 레코드가 아닌 값으로 담아 ERROR로 남긴다")
    void judge_logsFailureAsError() {
        // given
        LogCapture log = LogCapture.of(UpdateRunningComboJudge.class);
        willThrow(new IllegalStateException("redis down"))
                .given(loadRunningComboSnapshotsPort).loadSnapshots(ROOM_ID);

        try {
            // when
            judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

            // then -> UserId[value=...]로 찍히면 수집 단계의 userId 검색에 걸리지 않는다
            assertThat(log.messages(Level.ERROR)).containsExactly(
                    "[러닝] 콤보 판정 실패: 처리하지 못한 예외 - roomId=" + ROOM_ID + ", userId=" + SENDER.value());
            assertThat(log.events(Level.ERROR).getFirst().getThrowableProxy()).isNotNull();
        } finally {
            log.stop();
        }
    }

    // 10초 전에도 달리던 보낸 사람과, 지금 막 관측한 20m 앞의 상대 — 둘 다 달리는 중이라 창 안이면 붙는다
    private void givenRunningSenderAndPeer() {
        Instant now = Instant.now();
        Instant before = now.minusSeconds(10);
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(SENDER, 900, before, 10, before, before, false, false),
                new RunningComboSnapshot(PEER, 1_020, now, 0, now, now, false, false)));
    }

    @Test
    @DisplayName("일시정지 경계를 넘은 배치는 표시가 없어도 이 판정에서 일시정지로 보고 붙이지 않는다")
    void judge_treatsPauseCrossingAsPaused() {
        // given -> 일시정지 신호(RUNNING_PAUSE)가 유실돼 표시가 없다
        givenRunningSenderAndPeer();

        // when -> 재개 뒤 첫 배치가 경계를 넘었다
        judge.judge(ROOM_ID, SENDER, new RunningComboSignal(1_000, true, true, true));

        // then -> 놓치면 일시정지 시간까지 함께 달린 것으로 세어진다
        assertThat(captureSavedSnapshot().paused()).isTrue();
        assertThat(captureUpdate().relations()).isEmpty();
    }

    @Test
    @DisplayName("경계를 넘지 않은 같은 배치는 그대로 붙는다")
    void judge_attachesWithoutPauseCrossing() {
        // given -> 위와 같은 상황에서 경계만 없다
        givenRunningSenderAndPeer();

        // when
        judge.judge(ROOM_ID, SENDER, RUNNING_AT_1000);

        // then -> 위 테스트의 대조군이다 — 끊긴 이유가 경계뿐임을 보인다
        assertThat(captureSavedSnapshot().paused()).isFalse();
        assertThat(captureUpdate().relations()).hasSize(1);
    }

    @Test
    @DisplayName("첫 배치가 경계를 넘었어도 이 판정에서 일시정지다")
    void judge_treatsPauseCrossingAsPausedOnFirstBatch() {
        // given -> 저장된 스냅샷이 없다

        // when
        judge.judge(ROOM_ID, SENDER, new RunningComboSignal(1_000, true, true, true));

        // then
        assertThat(captureSavedSnapshot().paused()).isTrue();
    }

    @Test
    @DisplayName("일시정지 표시가 있던 참가자가 경계를 넘은 배치로 돌아오면 표시는 다음 배치가 지운다")
    void judge_keepsPauseMarkOnCrossingBatch() {
        // given -> 일시정지 신호를 받아 표시가 있다
        givenSenderSnapshot(900, Instant.now().minusSeconds(30), 0, true);

        // when -> 재개 뒤 첫 배치 — 관측은 있지만 경계를 넘었다
        judge.judge(ROOM_ID, SENDER, new RunningComboSignal(1_000, true, true, true));

        // then -> 이 판정은 일시정지다. 지우면 다음 배치 전까지 일시정지 근거가 사라진다
        assertThat(captureSavedSnapshot().paused()).isTrue();
        verify(markRunningComboPausedPort, never()).clearPaused(anyLong(), any());
    }

    private void givenSenderSnapshot(double meters, Instant recordedAt, double speed, boolean paused) {
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(
                        SENDER, meters, recordedAt, speed, recordedAt, recordedAt, paused, false)));
    }

    // 지금 막 관측했고 의심점도 옮겨진 상대 — 달리는 중
    private void givenRunningPeer(double meters) {
        Instant now = Instant.now();
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(PEER, meters, now, 0, now, now, false, false)));
    }

    private RunningComboSnapshot captureSavedSnapshot() {
        ArgumentCaptor<RunningComboSnapshot> captor =
                ArgumentCaptor.forClass(RunningComboSnapshot.class);
        verify(saveRunningComboSnapshotPort).saveSnapshot(eq(ROOM_ID), captor.capture());
        return captor.getValue();
    }

    private RunningComboUpdate captureUpdate() {
        ArgumentCaptor<RunningComboUpdate> captor =
                ArgumentCaptor.forClass(RunningComboUpdate.class);
        verify(publishRunningComboPort).publish(eq(ROOM_ID), captor.capture());
        return captor.getValue();
    }
}
