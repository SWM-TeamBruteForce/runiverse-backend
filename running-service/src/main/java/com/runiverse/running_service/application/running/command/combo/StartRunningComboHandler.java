package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.in.StartRunningComboUsecase;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartRunningComboHandler implements StartRunningComboUsecase {

    private final LoadRunningComboSnapshotsPort loadRunningComboSnapshotsPort;
    private final LoadRunningDistancePort loadRunningDistancePort;
    private final UpdateRunningComboJudge updateRunningComboJudge;

    // RUNNING_START에서 한 번 판정해 출발선의 콤보를 즉시 세운다 —
    // 안 하면 첫 좌표 배치(약 10초)까지 콤보가 비어 있다.
    // 판정 자체는 배치 경로와 같은 UpdateRunningComboJudge를 그대로 쓴다:
    // 좌표가 없는 참가자는 직전 스냅샷도 없어 속도 0으로 기록되는데, 그것이 곧 베이스라인이다
    @Override
    public void handle(StartRunningComboCommand command) {
        UserId starter = new UserId(command.userId());
        try {
            // 이미 스냅샷이 있으면 재연결이다 — 절대 건드리지 않는다.
            // 낡은 위치를 "지금 값"으로 되살리면 그 지점을 지나는 사람과 유령 콤보가 붙고,
            // 낡아서 fresh에서 빠진 채로 판정이 돌면 살아 있던 콤보의 봐주기가 깎여 끊긴다.
            // 클라가 로컬 트랙을 재전송하므로 다음 배치가 제자리로 돌려놓는다.
            // 중복 RUNNING_START도 여기서 걸려 멱등이 된다
            boolean alreadyJoined = loadRunningComboSnapshotsPort.loadSnapshots(command.runningRoomId())
                    .stream()
                    .anyMatch(snapshot -> snapshot.userId().equals(starter));
            if (alreadyJoined) {
                return;
            }
            // 0을 지어내지 않고 저장된 누적을 읽는다 — 첫 진입이면 자연히 0이다
            double meters = loadRunningDistancePort
                    .loadDistance(command.runningRoomId(), starter)
                    .meters();
            updateRunningComboJudge.judge(command.runningRoomId(), starter, meters);
        } catch (RuntimeException e) {
            // 콤보는 곁가지다 — 못 세웠다고 러닝 시작을 막지 않는다.
            // 첫 좌표 배치가 도착하면 원래 경로로 콤보가 붙는다
            log.warn("러닝 콤보 시작 판정 실패 — roomId={}, userId={}",
                    command.runningRoomId(), starter, e);
        }
    }
}
