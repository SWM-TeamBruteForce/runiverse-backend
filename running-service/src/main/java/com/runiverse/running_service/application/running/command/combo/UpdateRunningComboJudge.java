package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.PublishRunningComboPort;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSnapshotPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateRunningComboJudge {

    private final LoadRunningComboSnapshotsPort loadRunningComboSnapshotsPort;
    private final SaveRunningComboSnapshotPort saveRunningComboSnapshotPort;
    private final LoadRunningComboPairsPort loadRunningComboPairsPort;
    private final SaveRunningComboPairsPort saveRunningComboPairsPort;
    private final PublishRunningComboPort publishRunningComboPort;
    private final RunningComboProperties properties;

    // 위치 배치가 도착할 때마다 방 전체를 판정한다 — 서버가 러닝 중 정보를 얻는 경로가 그것뿐이라
    // 판정 해상도는 배치 주기와 같고, 별도 스케줄러를 두지 않는다.
    // 어떤 실패도 밖으로 내보내지 않는다: 좌표는 이미 저장됐고 진행 통지도 나간 뒤라
    // 화면 표시 하나 때문에 클라가 ERROR를 받으면 안 된다
    public void judge(Long runningRoomId, UserId sender, double senderMeters) {
        try {
            Instant now = Instant.now();
            List<RunningComboSnapshot> stored = loadRunningComboSnapshotsPort.loadSnapshots(runningRoomId);
            // 읽기가 쓰기보다 먼저다 — 방 전체를 읽어 온 목록 안에 내 직전 값도 들어 있어서
            // 속도를 구하려고 따로 조회할 필요가 없다
            RunningComboSnapshot updated = snapshotOf(sender, senderMeters, stored, now);
            saveRunningComboSnapshotPort.saveSnapshot(runningRoomId, updated);
            RunningComboEvaluation evaluation = RunningComboEvaluator.evaluate(
                    sender,
                    replace(stored, updated),
                    loadRunningComboPairsPort.loadPairs(runningRoomId),
                    now,
                    properties);
            // 저장이 발행보다 먼저다 — 화면에 띄운 콤보를 서버가 모르는 상태가 되면 안 된다
            saveRunningComboPairsPort.savePairs(runningRoomId, evaluation.pairs());
            publishRunningComboPort.publish(runningRoomId, evaluation.update());
        } catch (RuntimeException e) {
            log.warn("러닝 콤보 판정 실패 — roomId={}, userId={}", runningRoomId, sender, e);
        }
    }

    private static RunningComboSnapshot snapshotOf(
            UserId sender, double meters, List<RunningComboSnapshot> stored, Instant now) {
        return stored.stream()
                .filter(snapshot -> snapshot.userId().equals(sender))
                .findFirst()
                .map(previous -> new RunningComboSnapshot(
                        sender, meters, now, speedFrom(previous, meters, now)))
                // 첫 배치는 직전 값이 없어 속도가 0이다 — 방금 보고한 값이라 밀 필요도 없다
                .orElseGet(() -> new RunningComboSnapshot(sender, meters, now, 0));
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
