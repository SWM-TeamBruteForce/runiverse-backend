package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.command.combo.UpdateRunningComboJudge;
import com.runiverse.running_service.application.running.port.in.UpdateRunningLocationUsecase;
import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.PublishRunningProgressPort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.RunningProgress;
import com.runiverse.running_service.application.running.port.out.SaveRunningDistancePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateRunningLocationHandler implements UpdateRunningLocationUsecase {

    private final AppendRunningTrackPort appendRunningTrackPort;
    private final LoadRunningDistancePort loadRunningDistancePort;
    private final SaveRunningDistancePort saveRunningDistancePort;
    private final PublishRunningProgressPort publishRunningProgressPort;
    private final UpdateRunningComboJudge updateRunningComboJudge;
    private final UpdateRunningFinishJudge updateRunningFinishJudge;

    @Override
    public UpdateRunningLocationResult handle(UpdateRunningLocationCommand command) {
        UserId userId = new UserId(command.userId());
        // 1. 좌표를 저장한다 — 가장 먼저다. 여기서 던지면 진행 통지도 건너뛰고 클라가 ERROR를 받는다
        appendRunningTrackPort.append(command.runningRoomId(), userId, command.points());
        // 2. 누적 거리를 이어 센다
        RunningDistance stored;
        try {
            stored = loadRunningDistancePort.loadDistance(command.runningRoomId(), userId);
        } catch (RuntimeException e) {
            // 누적을 못 읽으면 이번 배치의 진행 표시만 거른다 — 좌표는 이미 저장됐고 저장된 누적도 그대로다.
            // 건너뛴 배치의 곡선은 다음 배치가 직선으로 이어 라이브 표시에서만 빠진다 — 최종 기록이 바로잡는다
            log.error("[러닝] 누적 거리 조회 실패: 처리하지 못한 예외 - roomId={}, userId={}",
                    command.runningRoomId(), userId.value(), e);
            // 누적을 모르면 목표 도달도 판정할 수 없다 — 다음 배치가 다시 판정한다
            return new UpdateRunningLocationResult(false);
        }
        RunningDistance updated = RunningDistanceAccumulator.accumulate(stored, command.points());
        saveRunningDistancePort.saveDistance(command.runningRoomId(), userId, updated);
        // 3. 방 참가자에게 진행 상황을 알린다
        publishRunningProgressPort.publish(command.runningRoomId(), new RunningProgress(
                command.userId(),
                updated.metersRounded(),
                command.targetDistanceMeters(),
                // 누적기가 거리에 반영한 좌표의 페이스다 — 배치가 통째로 재전송분이면
                // 직전 값이 그대로 유지된다
                updated.lastPaceSecondsPerKm(),
                false));   // TODO: 일시정지 고정값 — RUNNING_PAUSE/RESUME을 만들 때 실제 상태로 교체한다
        // 4. 콤보를 판정한다 — 진행 통지 뒤에 둔다. 곁가지라 앞에 두면 판정이 느려질 때 진행 표시까지 늦어진다
        updateRunningComboJudge.judge(command.runningRoomId(), userId, updated.meters());
        // 5. 목표 도달을 판정한다 — 맨 마지막이다. 목표를 넘은 이 배치까지 트랙에 저장되고
        //    진행 통지도 나간 뒤여야 한다
        boolean finished = updateRunningFinishJudge.judge(
                command.runningRoomId(), userId, command.targetDistanceMeters(), updated.meters());
        return new UpdateRunningLocationResult(finished);
    }
}
