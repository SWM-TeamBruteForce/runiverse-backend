package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.port.in.UpdateRunningLocationUsecase;
import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.PublishRunningProgressPort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import com.runiverse.running_service.application.running.port.out.SaveRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class UpdateRunningLocationHandler implements UpdateRunningLocationUsecase {

    private final AppendRunningTrackPort appendRunningTrackPort;
    private final LoadRunningDistancePort loadRunningDistancePort;
    private final SaveRunningDistancePort saveRunningDistancePort;
    private final PublishRunningProgressPort publishRunningProgressPort;

    @Override
    public void handle(UpdateRunningLocationCommand command) {
        UserId userId = new UserId(command.userId());
        // 좌표 저장이 먼저다 — 여기서 던지면 진행 통지도 건너뛰고 클라가 ERROR를 받는다
        appendRunningTrackPort.append(command.runningRoomId(), userId, command.points());
        RunningDistance stored;
        try {
            stored = loadRunningDistancePort.loadDistance(command.runningRoomId(), userId);
        } catch (RuntimeException e) {
            // 누적을 못 읽으면 이번 배치의 진행 표시만 거른다 — 좌표는 이미 저장됐고 저장된 누적도 그대로다.
            // 건너뛴 배치의 곡선은 다음 배치가 직선으로 이어 라이브 표시에서만 빠진다 — 최종 기록이 바로잡는다
            return;
        }
        RunningDistance updated = RunningDistanceAccumulator.accumulate(stored, command.points());
        saveRunningDistancePort.saveDistance(command.runningRoomId(), userId, updated);
        publishRunningProgressPort.publish(command.runningRoomId(), new RunningProgress(
                command.userId(),
                updated.metersRounded(),
                command.targetDistanceMeters(),
                latestPace(command),
                false));   // TODO: 일시정지 미구현 — RUNNING_PAUSE/RESUME을 만들 때 실제 상태로 교체한다
    }

    // 마지막 좌표의 값을 그대로 옮긴다 — 단말이 못 재면 null이다
    private Integer latestPace(UpdateRunningLocationCommand command) {
        return command.points().stream()
                .max(Comparator.comparingLong(TrackPoint::sequence))
                .map(TrackPoint::currentPaceSecondsPerKm)
                .orElse(null);
    }
}
