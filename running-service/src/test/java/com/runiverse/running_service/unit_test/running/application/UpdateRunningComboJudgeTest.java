package com.runiverse.running_service.unit_test.running.application;

import com.runiverse.running_service.application.running.command.combo.RunningComboProperties;
import com.runiverse.running_service.application.running.command.combo.UpdateRunningComboJudge;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.PublishRunningComboPort;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSnapshotPort;
import com.runiverse.running_service.domain.common.vo.UserId;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 판정 규칙은 RunningComboEvaluator가 맡고, 여기서는 그 앞뒤 — 속도 계산과
// 실패를 삼키는 계약을 본다. 안에서 Instant.now()를 부르므로 경과 시간은 허용 오차로 본다
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

    @Mock
    private LoadRunningComboSnapshotsPort loadRunningComboSnapshotsPort;

    @Mock
    private SaveRunningComboSnapshotPort saveRunningComboSnapshotPort;

    @Mock
    private LoadRunningComboPairsPort loadRunningComboPairsPort;

    @Mock
    private SaveRunningComboPairsPort saveRunningComboPairsPort;

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
                publishRunningComboPort,
                PROPERTIES);
    }

    @Test
    @DisplayName("첫 배치는 직전 값이 없어 속도가 0이다")
    void judge_savesZeroSpeedOnFirstBatch() {
        // given -> 저장된 스냅샷이 없다

        // when
        judge.judge(ROOM_ID, SENDER, 1_000);

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
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(SENDER, 900, Instant.now().minusSeconds(100), 0)));

        // when
        judge.judge(ROOM_ID, SENDER, 1_000);

        // then -> 100m / 100초 = 1.0m/s. Instant.now() 호출 간격만큼 오차가 있다
        assertThat(captureSavedSnapshot().speedMetersPerSecond()).isCloseTo(1.0, within(0.05));
    }

    @Test
    @DisplayName("누적 거리가 줄어 보여도 속도는 음수가 되지 않는다")
    void judge_clampsNegativeSpeedToZero() {
        // given -> 저장이 밀려 옛 값을 읽으면 증가분이 음수로 나온다.
        // 음수 속도는 보정을 뒤로 밀어 멀쩡한 콤보를 끊는다
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(SENDER, 2_000, Instant.now().minusSeconds(10), 5)));

        // when
        judge.judge(ROOM_ID, SENDER, 1_000);

        // then
        assertThat(captureSavedSnapshot().speedMetersPerSecond()).isZero();
    }

    @Test
    @DisplayName("직전 기준 시각이 지금보다 뒤면 직전 속도를 이어 쓴다")
    void judge_keepsPreviousSpeedWhenElapsedIsNotPositive() {
        // given -> 인스턴스 간 시계가 어긋나면 직전 기준 시각이 미래일 수 있다.
        // 경과가 0 이하면 나눌 수 없다
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(SENDER, 900, Instant.now().plusSeconds(10), 3.5)));

        // when
        judge.judge(ROOM_ID, SENDER, 1_000);

        // then
        assertThat(captureSavedSnapshot().speedMetersPerSecond()).isEqualTo(3.5);
    }

    @Test
    @DisplayName("판정은 방금 저장한 값으로 돈다")
    void judge_evaluatesWithTheUpdatedSnapshot() {
        // given -> 저장된 목록에 보낸 사람이 없어도 이번 배치 값으로 비교해야 한다
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(PEER, 1_020, Instant.now(), 0)));

        // when
        judge.judge(ROOM_ID, SENDER, 1_000);

        // then -> 20m 차이라 콤보가 붙고 둘 다 받는다
        RunningComboUpdate update = captureUpdate();
        assertThat(update.relations()).hasSize(1);
        assertThat(update.relations().getFirst().gapMeters()).isEqualTo(20);
        assertThat(update.recipients()).containsExactlyInAnyOrder(SENDER.value(), PEER.value());
    }

    @Test
    @DisplayName("관계 저장이 발행보다 먼저다")
    void judge_savesPairsBeforePublishing() {
        // given -> 화면에 띄운 콤보를 서버가 모르는 상태가 되면 안 된다
        given(loadRunningComboSnapshotsPort.loadSnapshots(ROOM_ID)).willReturn(List.of(
                new RunningComboSnapshot(PEER, 1_020, Instant.now(), 0)));

        // when
        judge.judge(ROOM_ID, SENDER, 1_000);

        // then
        InOrder order = inOrder(saveRunningComboPairsPort, publishRunningComboPort);
        order.verify(saveRunningComboPairsPort).savePairs(anyLong(), any());
        order.verify(publishRunningComboPort).publish(anyLong(), any());
    }

    @Test
    @DisplayName("읽기가 실패해도 밖으로 던지지 않는다")
    void judge_swallowsLoadFailure() {
        // given -> 좌표는 이미 저장됐고 진행 통지도 나간 뒤라
        // 화면 표시 하나 때문에 클라가 ERROR를 받으면 안 된다
        willThrow(new IllegalStateException("redis down"))
                .given(loadRunningComboSnapshotsPort).loadSnapshots(ROOM_ID);

        // when & then
        assertThatCode(() -> judge.judge(ROOM_ID, SENDER, 1_000)).doesNotThrowAnyException();
        // 판정을 못 했으므로 아무것도 내보내지 않는다 — 빈 통을 보내면 화면의 콤보가 지워진다
        verify(publishRunningComboPort, never()).publish(anyLong(), any());
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
