package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LockMatchRoomPort {

    // 스캔과 합류 사이에 자리가 찰 수 있다 — 확정 직전에 방을 잠그고 다시 읽는다.
    // 세션까지 복원해야 join()이 중복 참가를 판정할 수 있다.
    // 초대 방은 초대도 함께 복원한다 — 초대는 방의 자식이라 이 잠금 하나로 그 방의 초대가 모두 줄을 서고,
    // 먼저 수락한 사람이 자리를 잡는 순서도 이 잠금이 정한다
    Optional<RunningRoom> lockById(RunningRoomId runningRoomId);
}
