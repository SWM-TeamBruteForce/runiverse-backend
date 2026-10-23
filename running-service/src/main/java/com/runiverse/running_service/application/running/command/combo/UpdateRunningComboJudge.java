package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.MarkRunningComboPausedPort;
import com.runiverse.running_service.application.running.port.out.PublishRunningComboPort;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSegmentsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSnapshotPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateRunningComboJudge {

    private final LoadRunningComboSnapshotsPort loadRunningComboSnapshotsPort;
    private final SaveRunningComboSnapshotPort saveRunningComboSnapshotPort;
    private final LoadRunningComboPairsPort loadRunningComboPairsPort;
    private final SaveRunningComboPairsPort saveRunningComboPairsPort;
    private final SaveRunningComboSegmentsPort saveRunningComboSegmentsPort;
    private final MarkRunningComboPausedPort markRunningComboPausedPort;
    private final PublishRunningComboPort publishRunningComboPort;
    private final RunningComboProperties properties;

    // 위치 배치가 도착할 때마다 방 전체를 판정한다 — 서버가 러닝 중 정보를 얻는 경로가 그것뿐이라
    // 판정 해상도는 배치 주기와 같고, 별도 스케줄러를 두지 않는다.
    // 어떤 실패도 밖으로 내보내지 않는다: 좌표는 이미 저장됐고 진행 통지도 나간 뒤라
    // 화면 표시 하나 때문에 클라가 ERROR를 받으면 안 된다
    public void judge(Long runningRoomId, UserId sender, RunningComboSignal signal) {
        try {
            Instant now = Instant.now();
            List<RunningComboSnapshot> stored = loadRunningComboSnapshotsPort.loadSnapshots(runningRoomId);
            // 읽기가 쓰기보다 먼저다 — 방 전체를 읽어 온 목록 안에 내 직전 값도 들어 있어서
            // 속도·갱신 시각을 이어 쓰려고 따로 조회할 필요가 없다
            Optional<RunningComboSnapshot> previous = stored.stream()
                    .filter(snapshot -> snapshot.userId().equals(sender))
                    .findFirst();
            RunningComboSnapshot updated = snapshotOf(sender, signal, previous, now);
            saveRunningComboSnapshotPort.saveSnapshot(runningRoomId, updated);
            // 관측이 다시 들어왔으면 일시정지 표시를 지운다 — 다시 달리는 중이 되는 것은 좌표로만이다.
            // 못 지우면 이번 판정을 건너뛴다: 다음 배치가 다시 지우고 판정한다
            if (previous.map(RunningComboSnapshot::paused).orElse(false) && !updated.paused()) {
                markRunningComboPausedPort.clearPaused(runningRoomId, sender);
            }
            RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                    sender,
                    replace(stored, updated),
                    loadRunningComboPairsPort.loadPairs(runningRoomId),
                    now,
                    properties);
            // 구간이 관계보다 먼저다 — 관계를 끊긴 채로 저장한 뒤 구간 저장이 실패하면 그 구간은 영영 사라진다.
            // 거꾸로면 관계가 이어진 채로 남아 다음 배치가 같은 구간을 다시 닫고, 같은 칸(시작 시각)에 덮어쓴다
            saveRunningComboSegmentsPort.saveSegments(runningRoomId, evaluation.segments());
            // 저장이 발행보다 먼저다 — 화면에 띄운 콤보를 서버가 모르는 상태가 되면 안 된다
            saveRunningComboPairsPort.savePairs(runningRoomId, evaluation.pairs());
            publishRunningComboPort.publish(runningRoomId, evaluation.update());
        } catch (RuntimeException e) {
            log.error("[러닝] 콤보 판정 실패: 처리하지 못한 예외 - roomId={}, userId={}", runningRoomId, sender.value(), e);
        }
    }

    // 관측·이동이 없던 배치는 직전 시각을 이어 쓴다 — 시각이 그대로 늙어 가야 서 있음·관측 없음이 된다
    private static RunningComboSnapshot snapshotOf(UserId sender, RunningComboSignal signal,
                                                   Optional<RunningComboSnapshot> previous, Instant now) {
        return previous
                .map(before -> new RunningComboSnapshot(
                        sender,
                        signal.meters(),
                        now,
                        speedFrom(before, signal.meters(), now),
                        signal.moved() ? now : before.movedAt(),
                        signal.observed() ? now : before.observedAt(),
                        // 경계를 넘은 배치는 표시가 없어도 이 판정에서 일시정지다 — 저장하지 않는 값이라
                        // 다음 배치부터는 표시·관측으로만 정해진다. 대가로 재개 후 첫 배치에서는 붙지 않는다
                        (before.paused() && !signal.observed()) || signal.pauseCrossed(),
                        // 종료는 관측이 와도 그대로다 — 끝난 사람의 좌표는 늦은 배치뿐이다
                        before.finished()))
                // 첫 배치는 직전 값이 없어 속도가 0이다 — 방금 보고한 값이라 밀 필요도 없다
                .orElseGet(() -> new RunningComboSnapshot(
                        sender,
                        signal.meters(),
                        now,
                        0,
                        signal.moved() ? now : null,
                        signal.observed() ? now : null,
                        signal.pauseCrossed(),
                        false));
    }

    private static double speedFrom(RunningComboSnapshot previous, double meters, Instant now) {
        long elapsedMillis = Duration.between(previous.recordedAt(), now).toMillis();
        if (elapsedMillis <= 0) {
            // 같은 밀리초에 두 배치가 겹치면 속도를 구할 수 없다 — 직전 속도를 이어 쓴다
            return previous.speedMetersPerSecond();
        }
        // 누적 거리는 줄어들지 않지만, 저장이 밀려 옛 값을 읽으면 음수가 나온다.
        // 음수 속도는 보정을 뒤로 밀어 멀쩡한 콤보를 끊으므로 0으로 막는다
        return Math.max(0, (meters - previous.meters()) / (elapsedMillis / 1000.0));
    }

    // 판정은 이번 배치를 반영한 값으로 돌아야 한다 — 방금 저장한 것과 같은 스냅샷으로 갈아끼운다
    private static List<RunningComboSnapshot> replace(
            List<RunningComboSnapshot> snapshots, RunningComboSnapshot updated) {
        return Stream.concat(
                snapshots.stream().filter(snapshot -> !snapshot.userId().equals(updated.userId())),
                Stream.of(updated)).toList();
    }
}
