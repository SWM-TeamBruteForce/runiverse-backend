package com.runiverse.running_service.application.match.port.out;

import com.runiverse.running_service.domain.running.room.RunningRoom;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Optional;

public interface LockMatchRoomPort {

    // 스캔과 합류 사이에 자리가 찰 수 있다 — 확정 직전에 방을 잠그고 다시 읽는다.
    // 세션까지 복원해야 join()이 중복 참가를 판정할 수 있다
    Optional<RunningRoom> lockById(RunningRoomId runningRoomId);
}
