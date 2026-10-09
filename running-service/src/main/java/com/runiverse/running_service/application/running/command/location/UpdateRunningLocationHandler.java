package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.command.combo.UpdateRunningComboJudge;
import com.runiverse.running_service.application.running.port.in.UpdateRunningLocationUsecase;
import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.ChangeLiveRunningStatusPort;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LoadLiveRunningStatusPort;
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
    private final ChangeLiveRunningStatusPort changeLiveRunningStatusPort;
    private final LoadLiveRunningStatusPort loadLiveRunningStatusPort;
    private final PublishRunningProgressPort publishRunningProgressPort;
    private final UpdateRunningComboJudge updateRunningComboJudge;
    private final UpdateRunningFinishJudge updateRunningFinishJudge;

    @Override
    public UpdateRunningLocationResult handle(UpdateRunningLocationCommand command) {
        UserId userId = new UserId(command.userId());
        // 0. 이미 끝난 참가자의 늦은 배치는 받지 않는다 — 종료 때 비운 버퍼가 다시 쌓이고,
        //    끝난 사람의 거리가 늘어나 상대 화면과 콤보 판정에 섞인다.
        //    끝났다고 답해 RUNNING_FINISHED를 다시 받은 클라가 로컬 트랙을 지우게 한다
        if (isFinished(command.runningRoomId(), userId)) {
            return UpdateRunningLocationResult.ofFinished();
        }
        // 1. 좌표를 저장한다 — 가장 먼저다. 여기서 던지면 진행 통지도 건너뛰고 클라가 ERROR를 받는다.
        //    처음 보는 좌표 수를 받아 둔다 — 재전송분만 온 배치인지 가르는 근거다
        int appended = appendRunningTrackPort.append(command.runningRoomId(), userId, command.points());
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
            return UpdateRunningLocationResult.ofRunning();
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
                liveStatus(command.runningRoomId(), userId, appended)));
        // 4. 콤보를 판정한다 — 진행 통지 뒤에 둔다. 곁가지라 앞에 두면 판정이 느려질 때 진행 표시까지 늦어진다
        updateRunningComboJudge.judge(command.runningRoomId(), userId, updated.meters());
        // 5. 목표 도달을 판정한다 — 맨 마지막이다. 목표를 넘은 이 배치까지 트랙에 저장되고
        //    진행 통지도 나간 뒤여야 한다
        return updateRunningFinishJudge.judge(
                command.runningRoomId(), userId, command.targetDistanceMeters(), updated.meters());
    }


    // 처음 보는 좌표가 왔으면 지금 뛰고 있다 — RESUME이 유실됐거나 옛 연결의 끊김이 늦게 반영된 것도
    // 여기서 풀린다. 재전송분만 온 배치는 상태를 건드리지 않는다.
    // 바뀌었어도 따로 알리지 않는다 — 바로 이 진행 통지가 새 상태를 싣고 나간다
    private LiveRunningStatus liveStatus(Long runningRoomId, UserId userId, int appended) {
        try {
            if (appended > 0) {
                return changeLiveRunningStatusPort
                        .change(runningRoomId, userId, LiveRunningStatus.RUNNING).current();
            }
            // 상태가 없으면 RUNNING으로 본다 — 좌표를 보내고 있다는 것 자체가 근거다
            return loadLiveRunningStatusPort.load(runningRoomId, userId)
                    .orElse(LiveRunningStatus.RUNNING);
        } catch (RuntimeException e) {
            // 표시용 값이다 — 못 읽었다고 진행 통지를 거르지 않는다. 좌표를 보내는 중이니 RUNNING으로 본다
            log.error("[러닝] 참가자 상태 갱신 실패: 처리하지 못한 예외 - roomId={}, userId={}",
                    runningRoomId, userId.value(), e);
            return LiveRunningStatus.RUNNING;
        }
    }

    // 상태를 못 읽으면 끝나지 않은 것으로 본다 — 표시용 값이 흔들려도 좌표 수용은 지금처럼 이어진다.
    // 끝난 참가자를 놓치면 버퍼만 TTL까지 남고, 끝나지 않은 참가자를 막으면 좌표가 사라진다
    private boolean isFinished(Long runningRoomId, UserId userId) {
        try {
            return loadLiveRunningStatusPort.load(runningRoomId, userId)
                    .filter(status -> status == LiveRunningStatus.FINISHED)
                    .isPresent();
        } catch (RuntimeException e) {
            log.error("[러닝] 참가자 상태 조회 실패: 처리하지 못한 예외 - roomId={}, userId={}",
                    runningRoomId, userId.value(), e);
            return false;
        }
    }
}
