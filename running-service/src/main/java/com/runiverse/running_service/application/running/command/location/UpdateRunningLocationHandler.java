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
        RunningDistance updated = RunningDistanceAccumulator.accumulate(
                loadRunningDistancePort.loadDistance(command.runningRoomId(), userId),
                command.points());
        saveRunningDistancePort.saveDistance(command.runningRoomId(), userId, updated);
        publishRunningProgressPort.publish(command.runningRoomId(), new RunningProgress(
                command.userId(),
                updated.metersRounded(),
                command.targetDistanceMeters(),
                latestPace(command),
                false));   // RUNNING_PAUSE/RESUME 구현 전까지 항상 false다(api-spec 5-D)
    }

    // 마지막 좌표의 값을 그대로 옮긴다 — 단말이 못 재면 null이다(api-spec 5-D)
    private Integer latestPace(UpdateRunningLocationCommand command) {
        return command.points().stream()
                .max(Comparator.comparingLong(TrackPoint::sequence))
                .map(TrackPoint::currentPaceSecondsPerKm)
                .orElse(null);
    }
}
