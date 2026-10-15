package com.runiverse.running_service.application.running.command.status;

import com.runiverse.running_service.application.running.common.LiveRunningStatusChanger;
import com.runiverse.running_service.application.running.port.in.ChangeLiveRunningStatusUsecase;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.MarkRunningComboPausedPort;
import com.runiverse.running_service.application.running.port.out.MarkRunningTrackPausedPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeLiveRunningStatusHandler implements ChangeLiveRunningStatusUsecase {

    private final LiveRunningStatusChanger liveRunningStatusChanger;
    private final MarkRunningComboPausedPort markRunningComboPausedPort;
    private final LoadRunningDistancePort loadRunningDistancePort;
    private final MarkRunningTrackPausedPort markRunningTrackPausedPort;

    // DB를 보지 않는다 — 끝난 참가자의 늦은 PAUSE는 FINISHED가 막고, 기록은 좌표 판정이 지킨다.
    // Redis만 쓰므로 트랜잭션 경계도 두지 않는다
    @Override
    public void handle(ChangeLiveRunningStatusCommand command) {
        UserId userId = new UserId(command.userId());
        liveRunningStatusChanger.change(command.runningRoomId(), userId,
                command.targetDistanceMeters(), command.status());
        // 일시정지는 콤보를 바로 끊는다 — 다음 판정이 이 표시를 본다. 관측이 다시 들어오면 판정이 지운다.
        // 신호는 끊는 쪽으로만 쓴다: 조작해 봐야 자기 콤보를 잃을 뿐이다.
        // RESUME은 표시를 지우지 않는다 — 다시 달리는 중이 되는 것은 좌표로만이다
        if (command.status() == LiveRunningStatus.PAUSED) {
            markPaused(command.runningRoomId(), userId);
            markTrackPaused(command.runningRoomId(), userId);
        }
    }

    private void markPaused(Long runningRoomId, UserId userId) {
        try {
            markRunningComboPausedPort.markPaused(runningRoomId, userId);
        } catch (RuntimeException e) {
            // 콤보는 곁가지다 — 표시를 못 남기면 좌표가 멈춰 관측 없음(보류)으로 남는다
            log.error("[러닝] 콤보 일시정지 표시 실패: 처리하지 못한 예외 - roomId={}, userId={}",
                    runningRoomId, userId.value(), e);
        }
    }

    // 멈추기 전 마지막 순번을 남긴다 — 트랙 필터가 이 뒤의 공백을 뺀다
    private void markTrackPaused(Long runningRoomId, UserId userId) {
        try {
            long lastSequence = loadRunningDistancePort.loadDistance(runningRoomId, userId).lastSequence();
            // 좌표를 받기 전이면 뺄 공백도 없다
            if (lastSequence < 0) {
                return;
            }
            markRunningTrackPausedPort.markPaused(runningRoomId, userId, lastSequence);
        } catch (RuntimeException e) {
            // 표시가 없으면 공백은 관측 안 된 칸으로 판정된다
            log.error("[러닝] 트랙 일시정지 표시 실패: 처리하지 못한 예외 - roomId={}, userId={}",
                    runningRoomId, userId.value(), e);
        }
    }
}
