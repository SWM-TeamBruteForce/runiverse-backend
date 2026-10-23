package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.command.combo.RunningComboSignal;
import com.runiverse.running_service.application.running.command.combo.UpdateRunningComboJudge;
import com.runiverse.running_service.application.running.common.TrackFilterProperties;
import com.runiverse.running_service.application.running.exception.RunningTrackUnavailableException;
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
    // 의심점 반경·관측 정확도 상한 — 트랙 필터의 정지 판정과 같은 값을 쓴다
    private final TrackFilterProperties trackFilterProperties;

    @Override
    public UpdateRunningLocationResult handle(UpdateRunningLocationCommand command) {
        UserId userId = new UserId(command.userId());
        // 0. 이미 끝난 참가자의 늦은 배치는 받지 않는다 — 종료 때 비운 버퍼가 다시 쌓이고,
        //    끝난 사람의 거리가 늘어나 상대 화면과 콤보 판정에 섞인다.
        //    끝났다고 답해 RUNNING_FINISHED를 다시 받은 클라가 로컬 트랙을 지우게 한다
        if (isFinished(command.runningRoomId(), userId)) {
            return UpdateRunningLocationResult.ofFinished();
        }
        // 1. 누적 상태를 먼저 읽는다 — 일시정지 구간 번호를 마지막으로 반영한 좌표와 비교해야 저장할지 정할 수 있다.
        //    못 읽으면 검증할 수 없으니 저장하지 않는다 — 원본은 클라 로컬 트랙에 남아 재연결로 다시 온다
        RunningDistance stored;
        try {
            stored = loadRunningDistancePort.loadDistance(command.runningRoomId(), userId);
        } catch (RuntimeException e) {
            log.error("[러닝] 누적 거리 조회 실패: 처리하지 못한 예외 - roomId={}, userId={}",
                    command.runningRoomId(), userId.value(), e);
            throw new RunningTrackUnavailableException();
        }
        // 2. 일시정지 구간 번호가 줄면 배치 전체를 거부한다 — 저장한 뒤에 거르면 깨진 좌표가 기록에 섞인다
        PauseSegmentGuard.validate(command.runningRoomId(), userId, stored, command.points());
        // 3. 좌표를 저장한다. 여기서 던지면 진행 통지도 건너뛰고 클라가 ERROR를 받는다.
        //    처음 보는 좌표 수를 받아 둔다 — 재전송분만 온 배치인지 가르는 근거다
        int appended = appendRunningTrackPort.append(command.runningRoomId(), userId, command.points());
        // 4. 누적 거리를 이어 센다

        RunningDistanceAccumulation accumulation =
                RunningDistanceAccumulator.accumulate(stored, command.points(), trackFilterProperties);
        RunningDistance updated = accumulation.distance();
        saveRunningDistancePort.saveDistance(command.runningRoomId(), userId, updated);
        // 5. 방 참가자에게 진행 상황을 알린다
        publishRunningProgressPort.publish(command.runningRoomId(), new RunningProgress(
                command.userId(),
                updated.metersRounded(),
                command.targetDistanceMeters(),
                // 누적기가 거리에 반영한 좌표의 페이스다 — 배치가 통째로 재전송분이면
                // 직전 값이 그대로 유지된다
                updated.lastPaceSecondsPerKm(),
                liveStatus(command.runningRoomId(), userId, appended)));
        // 6. 콤보를 판정한다 — 진행 통지 뒤에 둔다. 곁가지라 앞에 두면 판정이 느려질 때 진행 표시까지 늦어진다.
        //    관측·이동 여부는 누적기가 같은 좌표를 훑으며 낸 값이다.
        //    재전송분만 온 배치는 판정하지 않는다 — 재연결하면 클라가 트랙 전체를 짧은 간격으로 다시 보내는데,
        //    이 배치가 속도를 0으로 덮으면 다음 배치의 밀린 거리가 그 짧은 간격에 뛴 것으로 계산된다
        if (updated.lastSequence() > stored.lastSequence()) {
            updateRunningComboJudge.judge(command.runningRoomId(), userId, new RunningComboSignal(
                    updated.meters(), accumulation.observed(), accumulation.moved(),
                    accumulation.pauseCrossed()));
        }
        // 7. 목표 도달을 판정한다 — 맨 마지막이다. 목표를 넘은 이 배치까지 트랙에 저장되고
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

    // 상태를 못 읽으면 끝나지 않은 것으로 본다 — 표시용 값이 흔들려도 좌표는 계속 받는다.
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
