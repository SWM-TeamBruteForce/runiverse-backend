package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.RunningComboPeer;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningComboReader {

    private final LoadRunningComboSnapshotsPort loadRunningComboSnapshotsPort;
    private final LoadRunningComboPairsPort loadRunningComboPairsPort;
    private final RunningComboProperties properties;

    // 지금 살아 있는 콤보를 받는 사람 기준으로 읽는다 — 판정하지 않으므로 아무 상태도 바뀌지 않는다.
    // UpdateRunningComboJudge와 같은 이유로 실패를 밖으로 내보내지 않는다:
    // 콤보는 곁가지인데 이걸 못 읽었다고 RUNNING_START ack가 실패하면 러닝 자체를 시작하지 못한다.
    // 빈 목록으로 내려가면 클라 화면에 콤보가 잠깐 비고, 다음 배치의 RUNNING_COMBO_UPDATED가 채운다
    public List<RunningComboPeer> read(Long runningRoomId, UserId recipient) {
        try {
            return RunningComboPeers.of(recipient, RunningComboEvaluator.liveRelations(
                    loadRunningComboSnapshotsPort.loadSnapshots(runningRoomId),
                    loadRunningComboPairsPort.loadPairs(runningRoomId),
                    Instant.now(),
                    properties));
        } catch (RuntimeException e) {
            log.warn("러닝 콤보 스냅샷 조회 실패 — roomId={}, userId={}", runningRoomId, recipient, e);
            return List.of();
        }
    }
}
